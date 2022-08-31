package net.folivo.trixnity.client.key

import com.benasher44.uuid.uuid4
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.IOlmDecrypter
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.days

private val log = KotlinLogging.logger {}

class OutgoingKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: IMatrixClientServerApiClient,
    private val olmDecrypter: IOlmDecrypter,
    private val keyBackupService: IKeyBackupService,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val currentSyncState: CurrentSyncState,
) : EventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleOutgoingKeyRequestAnswer)
        api.sync.subscribeAfterSyncResponse(::cancelOldOutgoingKeyRequests)
        api.sync.subscribe(::handleChangedSecrets)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { requestSecretKeysWhenCrossSigned() }
        scope.coroutineContext.job.invokeOnCompletion {
            olmDecrypter.unsubscribe(::handleOutgoingKeyRequestAnswer)
            api.sync.unsubscribeAfterSyncResponse(::cancelOldOutgoingKeyRequests)
            api.sync.unsubscribe(::handleChangedSecrets)
        }
    }

    internal suspend fun requestSecretKeys() {
        val missingSecrets = SecretType.values()
            .subtract(keyStore.secrets.value.keys)
            .subtract(keyStore.allSecretKeyRequests.value.mapNotNull { request ->
                request.content.name?.let { SecretType.ofId(it) }
            }.toSet())
        if (missingSecrets.isEmpty()) {
            log.debug { "there are no missing secrets or they are already requested" }
            return
        }
        val receiverDeviceIds = keyStore.getDeviceKeys(ownUserId)
            ?.filter { it.value.trustLevel == KeySignatureTrustLevel.CrossSigned(true) }
            ?.map { it.value.value.signed.deviceId }?.minus(ownDeviceId)?.toSet()
        if (receiverDeviceIds.isNullOrEmpty()) {
            log.debug { "there are no receivers, that we can request secret keys from" }
            return
        }
        missingSecrets.map { missingSecret ->
            val requestId = uuid4().toString()
            val request = SecretKeyRequestEventContent(
                name = missingSecret.id,
                action = KeyRequestAction.REQUEST,
                requestingDeviceId = ownDeviceId,
                requestId = requestId
            )
            log.debug { "send secret key request (${missingSecret.id}) to $receiverDeviceIds" }
            api.users.sendToDevice(mapOf(ownUserId to receiverDeviceIds.associateWith { request }))
                .onSuccess {
                    keyStore.addSecretKeyRequest(
                        StoredSecretKeyRequest(request, receiverDeviceIds, Clock.System.now())
                    )
                }.getOrThrow()
        }
    }

    internal suspend fun requestSecretKeysWhenCrossSigned() {
        currentSyncState.retryInfiniteWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed request secrets" } },
        ) {
            coroutineScope {
                keyStore.getDeviceKey(ownUserId, ownDeviceId, this).collect { deviceKeys ->
                    if (deviceKeys?.trustLevel == KeySignatureTrustLevel.CrossSigned(true)) {
                        requestSecretKeys()
                    }
                }
            }
        }
    }

    internal suspend fun handleOutgoingKeyRequestAnswer(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeySendEventContent) {
            log.debug { "handle outgoing key request answer ${content.requestId}" }
            val (senderDeviceId, senderTrustLevel) = keyStore.getDeviceKeys(ownUserId)?.firstNotNullOfOrNull {
                if (it.value.value.get<Key.Ed25519Key>()?.value == event.decrypted.senderKeys.get<Key.Ed25519Key>()?.value)
                    it.key to it.value.trustLevel
                else null
            } ?: (null to null)
            if (senderDeviceId == null) {
                log.warn { "could not derive sender device id from keys ${event.decrypted.senderKeys}" }
                return
            }
            if (!(senderTrustLevel is KeySignatureTrustLevel.CrossSigned && senderTrustLevel.verified || senderTrustLevel is KeySignatureTrustLevel.Valid && senderTrustLevel.verified)) {
                log.warn { "received a key from $senderDeviceId, but we don't trust that device ($senderTrustLevel)" }
                return
            }
            val request = keyStore.allSecretKeyRequests.value
                .firstOrNull { it.content.requestId == content.requestId }
            if (request?.receiverDeviceIds?.contains(senderDeviceId) != true) {
                log.warn { "received a key from $senderDeviceId, that we did not requested (or request is too old and we already deleted it)" }
                return
            }

            val secretType = request.content.name?.let { SecretType.ofId(it) }
            val publicKeyMatches = when (secretType) {
                SecretType.M_CROSS_SIGNING_USER_SIGNING, SecretType.M_CROSS_SIGNING_SELF_SIGNING -> {
                    val generatedPublicKey = try {
                        freeAfter(OlmPkSigning.create(content.secret)) { it.publicKey }
                    } catch (error: Throwable) {
                        log.warn(error) { "could not generate public key from received secret" }
                        return
                    }
                    val crossSigningKeyType =
                        if (secretType == SecretType.M_CROSS_SIGNING_SELF_SIGNING) CrossSigningKeysUsage.SelfSigningKey else CrossSigningKeysUsage.UserSigningKey
                    val originalPublicKey = keyStore.getCrossSigningKey(ownUserId, crossSigningKeyType)
                        ?.value?.signed?.get<Key.Ed25519Key>()?.value
                    originalPublicKey != null && originalPublicKey == generatedPublicKey
                }

                SecretType.M_MEGOLM_BACKUP_V1 -> {
                    api.keys.getRoomKeysVersion().map {
                        keyBackupService.keyBackupCanBeTrusted(it, content.secret)
                    }.onFailure { log.warn { "could not retrieve key backup version" } }
                        .getOrElse { false }
                }

                null -> false
            }
            if (secretType == null || !publicKeyMatches) {
                log.warn { "generated public key of secret ${request.content.name} did not match the original" }
                return
            }
            val encryptedSecret = secretType.getEncryptedSecret(globalAccountDataStore)
            if (encryptedSecret == null) {
                log.warn { "could not find encrypted secret" }
                return
            }
            keyStore.secrets.update {
                it + (secretType to StoredSecret(encryptedSecret, content.secret))
            }
            val cancelRequestTo = request.receiverDeviceIds - senderDeviceId
            log.debug { "stored secret $secretType and cancel outgoing key request to $cancelRequestTo" }
            if (cancelRequestTo.isNotEmpty()) {
                api.users.sendToDevice(
                    mapOf(
                        ownUserId to cancelRequestTo.associateWith { request.content.copy(action = KeyRequestAction.REQUEST_CANCELLATION) }
                    )
                ).getOrThrow()
            }
        }
    }

    internal suspend fun cancelOldOutgoingKeyRequests(syncResponse: Sync.Response) {
        keyStore.allSecretKeyRequests.value.forEach {
            if ((it.createdAt + 1.days) < Clock.System.now()) {
                log.debug { "cancel outgoing key request ${it.content.requestId}" }
                cancelStoredSecretKeyRequest(it)
            }
        }
    }

    private suspend fun cancelStoredSecretKeyRequest(request: StoredSecretKeyRequest) {
        val cancelRequest = request.content.copy(action = KeyRequestAction.REQUEST_CANCELLATION)
        log.info { "cancel old outgoing key request $request" }
        api.users.sendToDevice(mapOf(ownUserId to request.receiverDeviceIds.associateWith { cancelRequest }))
            .onSuccess {
                keyStore.deleteSecretKeyRequest(cancelRequest.requestId)
            }.getOrThrow()
    }

    internal suspend fun handleChangedSecrets(event: Event<out SecretEventContent>) {
        log.debug { "handle changed secrets" }
        val secretType =
            api.eventContentSerializerMappings.globalAccountData.find { event.content.instanceOf(it.kClass) }
                ?.let { SecretType.ofId(it.type) }
        if (secretType != null) {
            val storedSecret = keyStore.secrets.value[secretType]
            if (storedSecret?.event != event) {
                keyStore.allSecretKeyRequests.value.filter { it.content.name == secretType.id }
                    .forEach { cancelStoredSecretKeyRequest(it) }
                keyStore.secrets.update { it - secretType }
            }
        }
    }
}