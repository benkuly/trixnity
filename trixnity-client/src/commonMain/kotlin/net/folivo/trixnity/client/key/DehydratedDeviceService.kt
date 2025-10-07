package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.isVerified
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.devices.DehydratedDeviceData
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.crypto.core.AesHmacSha2EncryptedData
import net.folivo.trixnity.crypto.core.decryptAesHmacSha2
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKey
import net.folivo.trixnity.crypto.driver.useAll
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.crypto.olm.*
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignServiceImpl
import net.folivo.trixnity.crypto.sign.SignServiceStore
import net.folivo.trixnity.crypto.sign.SignWith.DeviceKey
import net.folivo.trixnity.crypto.sign.SignWith.KeyPair
import net.folivo.trixnity.crypto.sign.sign
import net.folivo.trixnity.utils.retry
import okio.ByteString.Companion.decodeBase64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.DehydratedDeviceService")

@MSC3814
class DehydratedDeviceService(
    private val api: MatrixClientServerApiClient,
    private val keyStore: KeyStore,
    private val userInfo: UserInfo,
    private val json: Json,
    private val olmStore: OlmStore,
    private val keyService: KeyService,
    private val signService: SignService,
    private val clock: Clock,
    private val config: MatrixClientConfiguration,
    private val driver: CryptoDriver,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (config.experimentalFeatures.enableMSC3814) {
            scope.launch {
                handleChanges()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun handleChanges() { // TODO unit test
        keyStore.getSecretsFlow()
            .map { it[SecretType.M_DEHYDRATED_DEVICE] }
            .distinctUntilChanged()
            .scan(listOf<StoredSecret?>()) { acc, new ->
                if (acc.isEmpty()) listOf(new)
                else listOf(new) + acc[0]
            }.filter { it.isNotEmpty() }
            .collect { encodedDehydratedDeviceSecrets ->
                val currentEncodedDehydratedDeviceSecret = encodedDehydratedDeviceSecrets[0]
                if (currentEncodedDehydratedDeviceSecret == null) {
                    log.warn { "skip device dehydration, because dehydrated device private key not present" }
                    return@collect
                }
                val dehydratedDeviceSecret =
                    currentEncodedDehydratedDeviceSecret.decryptedPrivateKey.decodeBase64()?.toByteArray()
                if (dehydratedDeviceSecret == null) {
                    log.warn { "skip device dehydration, because dehydrated device private key could not be decoded" }
                    return@collect
                }
                val dehydratedTrustLevel =
                    combine(
                        keyStore.getDeviceKeys(userInfo.userId)
                            .filterNotNull()
                            .map { deviceKeys ->
                                deviceKeys[userInfo.deviceId]?.trustLevel to
                                        deviceKeys.values.find { it.value.signed.dehydrated == true }?.trustLevel
                            }
                            .distinctUntilChanged(),
                        keyService.bootstrapRunning,
                    ) { trustLevels, bootstrapRunning ->
                        Pair(trustLevels, bootstrapRunning)
                    }.transform { (trustLevels, bootstrapRunning) ->
                        if (bootstrapRunning) {
                            log.debug { "skip device dehydration, because bootstrap still running" }
                            return@transform
                        }
                        val ownTrustLevel = trustLevels.first
                        if (ownTrustLevel !is CrossSigned || ownTrustLevel.isVerified.not()) {
                            log.debug { "skip device dehydration, because own device key not signed (yet)" }
                            return@transform
                        }
                        emit(trustLevels.second)
                    }.first()

                when {
                    dehydratedTrustLevel == null -> {
                        log.debug { "create new dehydrate device because missing" }
                        tryDehydrateDevice(dehydratedDeviceSecret)
                    }

                    dehydratedTrustLevel !is CrossSigned || dehydratedTrustLevel.isVerified.not() -> {
                        log.debug { "create new dehydrate device because current one is untrusted" }
                        tryDehydrateDevice(dehydratedDeviceSecret)
                    }

                    encodedDehydratedDeviceSecrets.size == 2 -> {
                        val previousEncodedDehydratedDeviceSecret = encodedDehydratedDeviceSecrets[1]
                        if (previousEncodedDehydratedDeviceSecret == null) {
                            log.debug { "rehydrate device because verification or bootstrap finished" }
                            tryRehydrateDevice(dehydratedDeviceSecret)
                        }
                        log.debug { "dehydrate device because secret has changed locally" }
                        tryDehydrateDevice(dehydratedDeviceSecret)
                    }

                    else -> {
                        log.debug { "skip device dehydration, because no action needed" }
                    }
                }
            }
    }

    internal suspend fun tryRehydrateDevice(dehydratedDeviceKey: ByteArray) {
        val currentDehydratedDevice =
            retry(
                onError = { error, delay -> log.warn(error) { "failed loading dehydrated device, try again in $delay" } },
            ) {
                api.device.getDehydratedDevice()
                    .fold(
                        onSuccess = { it },
                        onFailure = {
                            if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) null
                            else throw it
                        }
                    )
            }
        if (currentDehydratedDevice == null) {
            log.debug { "no dehydrated device found" }
            return
        }
        log.debug { "rehydrate existing device" }

        when (val deviceData = currentDehydratedDevice.deviceData) {
            is DehydratedDeviceData.DehydrationV2Compatibility -> {
                val olmAccountPickle = try {
                    decryptAesHmacSha2(
                        AesHmacSha2EncryptedData(
                            iv = deviceData.iv,
                            ciphertext = deviceData.encryptedDevicePickle,
                            mac = deviceData.mac
                        ),
                        dehydratedDeviceKey, deviceData.algorithm
                    ).decodeToString()
                } catch (e: Exception) {
                    log.warn(e) { "could not decrypt rehydrated device account pickle" }
                    return
                }
                val rehydratedUserInfo = try {
                    driver.olm.account.fromPickle(olmAccountPickle).use { account ->
                        useAll(
                            { account.ed25519Key },
                            { account.curve25519Key },
                        ) { signingKey, identityKey ->
                            val deviceId = identityKey.base64

                            UserInfo(
                                userInfo.userId,
                                deviceId,
                                Key.of(deviceId, signingKey),
                                Key.of(deviceId, identityKey)
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.warn(e) { "could not load rehydrated device account" }
                    return
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
                    userInfo = rehydratedUserInfo,
                    eventEmitter = eventEmitter,
                    olmKeysChangeEmitter = object : OlmKeysChangeEmitter {
                        override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit): () -> Unit =
                            { }
                    },
                    decrypter = OlmDecrypterImpl(
                        OlmEncryptionServiceImpl(
                            userInfo = rehydratedUserInfo,
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
                            driver = driver,
                        )
                    ),
                    signService = signService,
                    requestHandler = object : OlmEventHandlerRequestHandler {
                        override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> =
                            Result.failure(IllegalStateException("unsupported operation for dehydrated device"))
                    },
                    store = dehydratedDeviceOlmStore,
                    clock = clock,
                    driver = driver,
                )
                coroutineScope {
                    olmEventHandler.startInCoroutineScope(this)
                    var nextBatch: String? = null
                    while (isActive) {
                        val eventBatch =
                            retry(
                                onError = { error, delay -> log.warn(error) { "failed loading rehydrated device events, try again in $delay" } },
                            ) {
                                api.device.getDehydratedDeviceEvents(
                                    deviceId = currentDehydratedDevice.deviceId,
                                    nextBatch = nextBatch
                                ).fold(
                                    onSuccess = { it },
                                    onFailure = {
                                        if (it is MatrixServerException) null
                                        else throw it
                                    }
                                )
                            } ?: break
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
    }

    internal suspend fun tryDehydrateDevice(dehydratedDeviceKey: ByteArray) {
        try {
            val userId = userInfo.userId

            driver.olm.account().use { olmAccount ->
                val deviceId = olmAccount.curve25519Key.use(Curve25519PublicKey::base64)
                val signingKey = olmAccount.ed25519Key.use { Key.of(deviceId, it) }
                val identityKey = olmAccount.curve25519Key.use { Key.of(deviceId, it) }

                log.debug { "create new dehydrated device $deviceId" }

                val dehydratedUserInfo = UserInfo(
                    userId,
                    deviceId,
                    signingKey,
                    identityKey,
                )
                val dehydratedDeviceSignService = SignServiceImpl(
                    userInfo = dehydratedUserInfo,
                    json = json,
                    store = object : SignServiceStore {
                        override suspend fun getOlmAccount(): String = olmAccount.pickle()
                        override suspend fun getOlmPickleKey(): String? = null
                    },
                    driver = driver,
                )
                olmAccount.generateOneTimeKeys(olmAccount.maxNumberOfOneTimeKeys)
                val oneTimeKeys = olmAccount.oneTimeKeys.toCurve25519Keys(dehydratedDeviceSignService)
                olmAccount.generateFallbackKey()
                val fallbackKey =
                    olmAccount.fallbackKey?.let(::mapOf).toCurve25519Keys(dehydratedDeviceSignService, true)
                olmAccount.markKeysAsPublished()

                log.debug { "wait for M_CROSS_SIGNING_SELF_SIGNING private key" }
                val selfSigningPrivateKey = withTimeoutOrNull(30.seconds) {
                    keyStore.getSecretsFlow().map { it[M_CROSS_SIGNING_SELF_SIGNING] }.filterNotNull().first()
                }?.decryptedPrivateKey
                if (selfSigningPrivateKey == null) {
                    log.warn { "could not find private key of $M_CROSS_SIGNING_SELF_SIGNING" }
                    return
                }
                val selfSigningPublicKey =
                    driver.key.ed25519SecretKey(selfSigningPrivateKey).use(Ed25519SecretKey::publicKey)
                        .use(Ed25519PublicKey::base64)

                val deviceKeys = DeviceKeys(
                    userId = userId,
                    deviceId = deviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = keysOf(dehydratedUserInfo.signingPublicKey, dehydratedUserInfo.identityPublicKey),
                    dehydrated = true,
                ).let {
                    dehydratedDeviceSignService.sign(it, DeviceKey) + dehydratedDeviceSignService.sign(
                        it, KeyPair(selfSigningPrivateKey, selfSigningPublicKey)
                    ).signatures
                }

                log.debug { "upload device" }
                retry(
                    onError = { error, delay -> log.warn(error) { "failed upload dehydrated device, try again in $delay" } },
                ) {
                    api.device.setDehydratedDevice(
                        deviceId = deviceId,
                        deviceData = with(
                            encryptAesHmacSha2(
                                content = olmAccount.pickle().encodeToByteArray(),
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
                    ).fold(
                        onSuccess = { it },
                        onFailure = {
                            if (it is MatrixServerException) null
                            else throw it
                        }
                    )
                }

                log.debug { "wait for dehydrated device keys to be marked as cross signed and verified" }
                val verifiedResult = withTimeoutOrNull(30.seconds) {
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

    private suspend fun Map<String, Curve25519PublicKey>?.toCurve25519Keys(
        signService: SignService, fallback: Boolean? = null
    ) = this?.takeIf { it.isNotEmpty() }?.let {
        Keys(it.map {
            signService.signCurve25519Key(
                keyId = it.key, keyValue = it.value.use(Curve25519PublicKey::base64), fallback = fallback
            )
        }.toSet())
    }
}