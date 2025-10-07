package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.utils.retryLoop
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.devices.DehydratedDeviceData
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.AesHmacSha2EncryptedData
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.decryptAesHmacSha2
import net.folivo.trixnity.crypto.key.get
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.nextString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.OutgoingSecretKeyRequestEventHandler")

class OutgoingSecretKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val keyBackupService: KeyBackupService,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val currentSyncState: CurrentSyncState,
    private val clock: Clock,
) : EventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleOutgoingKeyRequestAnswer).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::cancelOldOutgoingKeyRequests).unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(subscriber = ::handleChangedSecrets).unsubscribeOnCompletion(scope)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { requestSecretKeysWhenCrossSigned() }
    }

    internal suspend fun requestSecretKeys() {
        val missingSecrets = SecretType.entries
            .filter { it.cacheable }
            .subtract(keyStore.getSecrets().keys)
            .subtract(keyStore.getAllSecretKeyRequests().mapNotNull { request ->
                request.content.name?.let { SecretType.ofId(it) }
            }.toSet())
        if (missingSecrets.isEmpty()) {
            log.debug { "there are no missing secrets or they are already requested" }
            return
        }
        val receiverDeviceIds = keyStore.getDeviceKeys(ownUserId).first()
            ?.filter {
                it.value.trustLevel.isVerified
                        && it.value.value.signed.deviceId != ownDeviceId
                        && @OptIn(MSC3814::class) it.value.value.signed.dehydrated != true
            }
            ?.map { it.value.value.signed.deviceId }?.toSet()
        if (receiverDeviceIds.isNullOrEmpty()) {
            log.debug { "there are no receivers, that we can request secret keys from" }
            return
        }
        missingSecrets.map { missingSecret ->
            val requestId = SecureRandom.nextString(22)
            val request = SecretKeyRequestEventContent(
                name = missingSecret.id,
                action = KeyRequestAction.REQUEST,
                requestingDeviceId = ownDeviceId,
                requestId = requestId
            )
            log.debug { "send secret key request (${missingSecret.id}) to $receiverDeviceIds" }
            // TODO should be encrypted (because this is meta data)
            api.user.sendToDevice(mapOf(ownUserId to receiverDeviceIds.associateWith { request }))
                .onSuccess {
                    keyStore.addSecretKeyRequest(
                        StoredSecretKeyRequest(request, receiverDeviceIds, clock.now())
                    )
                }.getOrThrow()
        }
    }

    internal suspend fun requestSecretKeysWhenCrossSigned() {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed request secret keys, try again in $delay" } },
        ) {
            keyStore.getDeviceKey(ownUserId, ownDeviceId).collect { deviceKeys ->
                if (deviceKeys?.trustLevel == KeySignatureTrustLevel.CrossSigned(true)) {
                    requestSecretKeys()
                }
            }
        }
    }

    internal suspend fun handleOutgoingKeyRequestAnswer(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeySendEventContent) {
            val requestId = content.requestId
            log.debug { "handle outgoing secret key request answer $requestId" }
            if (keyStore.getAllSecretKeyRequests().none { it.content.requestId == requestId }) {
                log.warn { "received a secret key request, but we don't requested one with the id $requestId" }
                return
            }
            val (senderDeviceId, senderTrustLevel) = keyStore.getDeviceKeys(ownUserId).first()?.firstNotNullOfOrNull {
                if (it.value.value.get<Key.Ed25519Key>()?.value == event.decrypted.senderKeys.get<Key.Ed25519Key>()?.value)
                    it.key to it.value.trustLevel
                else null
            } ?: (null to null)
            if (senderDeviceId == null) {
                log.warn { "could not derive sender device id from keys ${event.decrypted.senderKeys}" }
                return
            }
            if (senderTrustLevel?.isVerified != true) {
                log.warn { "received a key from $senderDeviceId, but we don't trust that device ($senderTrustLevel)" }
                return
            }
            val request = keyStore.getAllSecretKeyRequests()
                .firstOrNull { it.content.requestId == requestId }
            if (request?.receiverDeviceIds?.contains(senderDeviceId) != true) {
                log.warn { "received a key from $senderDeviceId, that we did not requested (or request is too old and we already deleted it)" }
                return
            }

            val secretType = request.content.name?.let { SecretType.ofId(it) }
            val publicKeyMatches = when (secretType) {
                SecretType.M_CROSS_SIGNING_USER_SIGNING, SecretType.M_CROSS_SIGNING_SELF_SIGNING -> {
                    val generatedPublicKey = try {
                        freeAfter(OlmPkSigning.create(content.secret)) { it.publicKey }
                    } catch (error: Exception) {
                        log.warn(error) { "could not generate public key from received secret" }
                        return
                    }
                    val crossSigningKeyType =
                        if (secretType == SecretType.M_CROSS_SIGNING_SELF_SIGNING) CrossSigningKeysUsage.SelfSigningKey else CrossSigningKeysUsage.UserSigningKey
                    val originalPublicKey = keyStore.getCrossSigningKey(ownUserId, crossSigningKeyType)
                        ?.value?.signed?.get<Key.Ed25519Key>()?.value
                    originalPublicKey != null && originalPublicKey.value == generatedPublicKey
                }

                SecretType.M_MEGOLM_BACKUP_V1 -> {
                    api.key.getRoomKeysVersion().map {
                        keyBackupService.keyBackupCanBeTrusted(it, content.secret)
                    }.onFailure { log.warn { "could not retrieve key backup version" } }
                        .getOrElse { false }
                }

                @OptIn(MSC3814::class) SecretType.M_DEHYDRATED_DEVICE -> @OptIn(MSC3814::class) {
                    try {
                        val deviceData = api.device.getDehydratedDevice()
                            .onFailure {
                                if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) {
                                    log.warn { "no dehydrated device found" }
                                    return
                                }
                            }.getOrThrow().deviceData
                        when (deviceData) {
                            is DehydratedDeviceData.DehydrationV2Compatibility -> {
                                val olmPickle = decryptAesHmacSha2(
                                    AesHmacSha2EncryptedData(
                                        iv = deviceData.iv,
                                        ciphertext = deviceData.encryptedDevicePickle,
                                        mac = deviceData.mac
                                    ),
                                    content.secret.decodeUnpaddedBase64Bytes(), deviceData.algorithm
                                ).decodeToString()
                                freeAfter(OlmAccount.unpickle(null, olmPickle)) {}
                                true
                            }

                            is DehydratedDeviceData.DehydrationV2,
                            is DehydratedDeviceData.Unknown -> {
                                log.warn { "dehydrated device algorithm ${deviceData.algorithm} not supported" }
                                false
                            }
                        }
                    } catch (error: Exception) {
                        log.warn(error) { "failed to use dehydrated device key to decrypt dehydrated device" }
                        false
                    }
                }

                SecretType.M_CROSS_SIGNING_MASTER, null -> {
                    log.warn { "ignore secret $secretType, because we are not interested in it" }
                    false
                }
            }
            if (secretType == null || !publicKeyMatches) {
                log.warn { "generated public key of secret ${request.content.name} did not match the original" }
                return
            }
            val encryptedSecret = secretType.getEncryptedSecret(globalAccountDataStore).first()
            if (encryptedSecret == null) {
                log.warn { "could not find encrypted secret" }
                return
            }
            keyStore.updateSecrets {
                it + (secretType to StoredSecret(encryptedSecret, content.secret))
            }

            request.cancelRequest(senderDeviceId)
        }
    }


    internal suspend fun cancelOldOutgoingKeyRequests() {
        keyStore.getAllSecretKeyRequests().forEach {
            if ((it.createdAt + 1.days) < clock.now()) {
                it.cancelRequest()
            }
        }
    }

    internal suspend fun handleChangedSecrets(event: ClientEvent<out SecretEventContent>) {
        log.debug { "handle changed secrets" }
        val secretType =
            api.eventContentSerializerMappings.globalAccountData.find { event.content.instanceOf(it.kClass) }
                ?.let { SecretType.ofId(it.type) }
        if (secretType != null) {
            val storedSecret = keyStore.getSecrets()[secretType]
            if (storedSecret?.event != event) {
                keyStore.getAllSecretKeyRequests().filter { it.content.name == secretType.id }
                    .forEach { it.cancelRequest() }
                keyStore.updateSecrets { it - secretType }
            }
        }
    }

    private suspend fun StoredSecretKeyRequest.cancelRequest(answeredFrom: String? = null) {
        val cancelRequestTo = receiverDeviceIds - setOfNotNull(answeredFrom)
        log.debug { "cancel outgoing secret key request to $cancelRequestTo" }
        if (cancelRequestTo.isNotEmpty()) {
            val cancelRequest = content.copy(action = KeyRequestAction.REQUEST_CANCELLATION)
            api.user.sendToDevice( // TODO should be encrypted (because this is meta data)
                mapOf(ownUserId to cancelRequestTo.associateWith { cancelRequest })
            ).getOrThrow()
        }
        keyStore.deleteSecretKeyRequest(content.requestId)
    }
}