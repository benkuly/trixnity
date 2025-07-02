package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.devices.DehydratedDeviceData
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.SelfSigningKey
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.crypto.core.AesHmacSha2EncryptedData
import net.folivo.trixnity.crypto.core.decryptAesHmacSha2
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.key.get
import net.folivo.trixnity.crypto.olm.*
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignServiceImpl
import net.folivo.trixnity.crypto.sign.SignServiceStore
import net.folivo.trixnity.crypto.sign.SignWith.DeviceKey
import net.folivo.trixnity.crypto.sign.SignWith.KeyPair
import net.folivo.trixnity.crypto.sign.sign
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.DehydratedDeviceService")

@MSC3814
class DehydratedDeviceService(
    private val api: MatrixClientServerApiClient,
    private val keyStore: KeyStore,
    private val userInfo: UserInfo,
    private val json: Json,
    private val olmStore: OlmStore,
    private val signService: SignService,
    private val clock: Clock,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (config.experimentalFeatures.enableMSC3814) {
            scope.launch {
                rehydrateDeviceOnSetup()
            }
        }
    }

    // TODO This is not safe against network issues and there is no retry mechanism.
    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun rehydrateDeviceOnSetup() {
        val dehydratedDeviceKeyHistory = keyStore.getSecretsFlow()
            .map { it[SecretType.M_DEHYDRATED_DEVICE] }
            .distinctUntilChanged()
            .scan(listOf<StoredSecret?>()) { acc, value -> acc + value }
            .first { it.lastOrNull() != null }

        if (dehydratedDeviceKeyHistory.size == 1) {
            log.debug { "skip device dehydration, because device already setup" }
            return
        }

        val dehydratedDeviceKey =
            dehydratedDeviceKeyHistory.last()?.decryptedPrivateKey?.decodeUnpaddedBase64Bytes()

        if (dehydratedDeviceKey == null) {
            log.warn { "skip device dehydration, because dehydrated device private key could not be decoded" }
            return
        }

        tryRehydrateDevice(dehydratedDeviceKey)
        tryDehydrateDevice(dehydratedDeviceKey)
    }

    internal suspend fun tryRehydrateDevice(dehydratedDeviceKey: ByteArray) {
        try {
            val currentDehydratedDevice = api.device.getDehydratedDevice()
                .onFailure {
                    if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) {
                        log.warn { "no dehydrated device found" }
                        return
                    }
                }.getOrThrow()
            log.debug { "rehydrate existing device" }

            when (val deviceData = currentDehydratedDevice.deviceData) {
                is DehydratedDeviceData.DehydrationV2Compatibility -> {
                    val olmAccountPickle = decryptAesHmacSha2(
                        AesHmacSha2EncryptedData(
                            iv = deviceData.iv,
                            ciphertext = deviceData.encryptedDevicePickle,
                            mac = deviceData.mac
                        ),
                        dehydratedDeviceKey, deviceData.algorithm
                    ).decodeToString()
                    val userInfo = freeAfter(OlmAccount.unpickle("", olmAccountPickle)) { olmAccount ->
                        val deviceId = olmAccount.identityKeys.curve25519
                        UserInfo(
                            userInfo.userId,
                            deviceId,
                            Ed25519Key(deviceId, olmAccount.identityKeys.ed25519),
                            Curve25519Key(deviceId, olmAccount.identityKeys.curve25519)
                        )
                    }
                    val dehydratedDeviceOlmStore = object : OlmStore by olmStore {
                        override suspend fun getOlmAccount(): String = olmAccountPickle
                        override suspend fun updateOlmAccount(updater: suspend (String) -> String) {
                            updater(olmAccountPickle)
                        }

                        private val temporaryOlmSessions = MutableStateFlow<Set<StoredOlmSession>?>(null)
                        override suspend fun updateOlmSessions(
                            senderKeyValue: KeyValue.Curve25519KeyValue,
                            updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
                        ) {
                            temporaryOlmSessions.update {
                                updater.invoke(it)
                            }
                        }
                    }
                    val eventEmitter = object : ClientEventEmitterImpl<List<ClientEvent<*>>>() {}
                    // TODO at the end, we only use ::handleOlmEncryptedRoomKeyEventContent and ::handleOlmEvents, so maybe just extract it
                    val olmEventHandler = OlmEventHandler(
                        userInfo = userInfo,
                        eventEmitter = eventEmitter,
                        olmKeysChangeEmitter = object : OlmKeysChangeEmitter {
                            override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit): () -> Unit =
                                { }
                        },
                        decrypter = OlmDecrypterImpl(
                            OlmEncryptionServiceImpl(
                                userInfo = userInfo,
                                json = json,
                                store = dehydratedDeviceOlmStore,
                                requests = object : OlmEncryptionServiceRequestHandler {
                                    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> =
                                        Result.failure(IllegalStateException("unsupported operation for dehydrated device"))

                                    override suspend fun sendToDevice(events: Map<UserId, Map<String, ToDeviceEventContent>>): Result<Unit> =
                                        Result.failure(IllegalStateException("unsupported operation for dehydrated device"))
                                },
                                signService = signService,
                                clock = clock,
                            )
                        ),
                        signService = signService,
                        requestHandler = object : OlmEventHandlerRequestHandler {
                            override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> =
                                Result.failure(IllegalStateException("unsupported operation for dehydrated device"))
                        },
                        store = dehydratedDeviceOlmStore,
                        clock = clock
                    )
                    coroutineScope {
                        olmEventHandler.startInCoroutineScope(this)
                        var nextBatch: String? = null
                        while (isActive) {
                            val eventBatch =
                                api.device.getDehydratedDeviceEvents(
                                    deviceId = currentDehydratedDevice.deviceId,
                                    nextBatch = nextBatch
                                ).getOrThrow()
                            nextBatch = eventBatch.nextBatch
                            if (eventBatch.events.isEmpty()) break
                            else eventEmitter.emit(eventBatch.events)
                        }
                        currentCoroutineContext().cancelChildren()
                    }
                }

                is DehydratedDeviceData.DehydrationV2, is DehydratedDeviceData.Unknown -> {
                    log.warn { "don't dehydrate device, because ${deviceData.algorithm} not supported" }
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            log.warn(exception) { "failed to rehydrate device" }
        }
    }

    internal suspend fun tryDehydrateDevice(dehydratedDeviceKey: ByteArray) {
        try {
            freeAfter(OlmAccount.create()) { olmAccount ->
                val userId = userInfo.userId
                val deviceId = olmAccount.identityKeys.curve25519

                log.debug { "create new dehydrated device $deviceId" }

                val userInfo = UserInfo(
                    userId,
                    deviceId,
                    Ed25519Key(deviceId, olmAccount.identityKeys.ed25519),
                    Curve25519Key(deviceId, olmAccount.identityKeys.curve25519)
                )
                val dehydratedDeviceSignService = SignServiceImpl(
                    userInfo = userInfo,
                    json = json,
                    store = object : SignServiceStore {
                        override suspend fun getOlmAccount(): String = olmAccount.pickle("")
                        override suspend fun getOlmPickleKey(): String = ""
                    }
                )
                olmAccount.generateOneTimeKeys(olmAccount.maxNumberOfOneTimeKeys)
                val oneTimeKeys = olmAccount.oneTimeKeys.curve25519.toCurve25519Keys(dehydratedDeviceSignService)
                olmAccount.generateFallbackKey()
                val fallbackKey =
                    olmAccount.unpublishedFallbackKey.curve25519.toCurve25519Keys(dehydratedDeviceSignService, true)
                olmAccount.markKeysAsPublished()

                val selfSigningPrivateKey = withTimeoutOrNull(10.seconds) {
                    keyStore.getSecretsFlow().map { it[M_CROSS_SIGNING_SELF_SIGNING] }.filterNotNull().first()
                }?.decryptedPrivateKey
                if (selfSigningPrivateKey == null) {
                    log.warn { "could not find private key of $M_CROSS_SIGNING_SELF_SIGNING" }
                    return
                }
                val selfSigningPublicKey =
                    withTimeoutOrNull(10.seconds) {
                        keyStore.getCrossSigningKeys(userId).map { crossSigningKeys ->
                            crossSigningKeys?.firstOrNull { it.value.signed.usage.contains(SelfSigningKey) }
                        }.mapNotNull { it?.value?.signed?.get<Ed25519Key>()?.id }
                            .first { expectedPublicKey ->
                                freeAfter(OlmPkSigning.create(selfSigningPrivateKey)) {
                                    it.publicKey == expectedPublicKey
                                }
                            }
                    }
                if (selfSigningPublicKey == null) {
                    log.warn { "could not find public key of $M_CROSS_SIGNING_SELF_SIGNING" }
                    return
                }

                val deviceKeys = DeviceKeys(
                    userId = userId,
                    deviceId = deviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = keysOf(userInfo.signingPublicKey, userInfo.identityPublicKey),
                    dehydrated = true,
                ).let {
                    dehydratedDeviceSignService.sign(it, DeviceKey) +
                            dehydratedDeviceSignService.sign(it, KeyPair(selfSigningPrivateKey, selfSigningPublicKey))
                                .signatures
                }

                api.device.setDehydratedDevice(
                    deviceId = deviceId,
                    deviceData = with(
                        encryptAesHmacSha2(
                            content = olmAccount.pickle("").encodeToByteArray(),
                            key = dehydratedDeviceKey,
                            name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
                        )
                    ) {
                        DehydratedDeviceData.DehydrationV2Compatibility(
                            iv = iv,
                            encryptedDevicePickle = ciphertext,
                            mac = mac,
                        )
                    },
                    deviceKeys = deviceKeys,
                    oneTimeKeys = oneTimeKeys,
                    fallbackKeys = fallbackKey,
                    initialDeviceDisplayName = "dehydrated device",
                ).getOrThrow()

                log.debug { "wait for dehydrated device keys to be marked as cross signed and verified" }
                val verifiedResult = withTimeoutOrNull(10.seconds) {
                    keyStore.getDeviceKey(userId, deviceId)
                        .mapNotNull { it?.trustLevel }
                        .first { it is CrossSigned && it.verified }
                    Unit
                }
                if (verifiedResult == null) {
                    log.warn { "dehydrated device keys were not marked as cross signed and verified" }
                } else {
                    log.debug { "dehydrated device keys successfully marked as cross signed and verified" }
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            log.warn(exception) { "failed to dehydrate device" }
        }
    }

    private suspend fun Map<String, String>.toCurve25519Keys(signService: SignService, fallback: Boolean? = null) =
        Keys(this.map {
            signService.signCurve25519Key(
                keyId = it.key,
                keyValue = it.value,
                fallback = fallback
            )
        }.toSet()).ifEmpty { null }
}