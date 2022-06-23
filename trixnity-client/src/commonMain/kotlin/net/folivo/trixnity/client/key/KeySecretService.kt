package net.folivo.trixnity.client.key

import com.benasher44.uuid.uuid4
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.SelfSigningKey
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.UserSigningKey
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.days

private val log = KotlinLogging.logger {}

interface IKeySecretService {
    suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    )
}

class KeySecretService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val olmEvents: IOlmEventService,
    private val keyBackup: IKeyBackupService,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: StateFlow<SyncState>,
    scope: CoroutineScope,
) : IKeySecretService {
    init {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { olmEvents.decryptedOlmEvents.collect(::handleEncryptedIncomingKeyRequests) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { olmEvents.decryptedOlmEvents.collect(::handleOutgoingKeyRequestAnswer) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { requestSecretKeysWhenCrossSigned() }
        api.sync.subscribeAfterSyncResponse {
            processIncomingKeyRequests()
            cancelOldOutgoingKeyRequests()
        }
        api.sync.subscribe(::handleChangedSecrets)
        api.sync.subscribe(::handleIncomingKeyRequests)
    }

    private val incomingSecretKeyRequests = MutableStateFlow<Set<SecretKeyRequestEventContent>>(setOf())

    internal fun handleEncryptedIncomingKeyRequests(event: IOlmService.DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeyRequestEventContent) {
            handleIncomingKeyRequests(ClientEvent.ToDeviceEvent(content, event.decrypted.sender))
        }
    }

    internal fun handleIncomingKeyRequests(event: ClientEvent<SecretKeyRequestEventContent>) {
        if (event is ClientEvent.ToDeviceEvent && event.sender == ownUserId) {
            val content = event.content
            when (content.action) {
                KeyRequestAction.REQUEST -> incomingSecretKeyRequests.update { it + content }
                KeyRequestAction.REQUEST_CANCELLATION -> incomingSecretKeyRequests
                    .update { oldRequests -> oldRequests.filterNot { it.requestId == content.requestId }.toSet() }
            }
        }
    }

    internal suspend fun processIncomingKeyRequests() {
        incomingSecretKeyRequests.value.forEach { request ->
            val requestingDeviceId = request.requestingDeviceId
            val senderTrustLevel = store.keys.getDeviceKey(ownUserId, requestingDeviceId)?.trustLevel
            if (senderTrustLevel is CrossSigned && senderTrustLevel.verified || senderTrustLevel is Valid && senderTrustLevel.verified) {
                val requestedSecret = request.name
                    ?.let { AllowedSecretType.ofId(it) }
                    ?.let { store.keys.secrets.value[it] }
                if (requestedSecret != null) {
                    log.info { "send incoming key request answer (${request.name}) to device $requestingDeviceId" }
                    api.users.sendToDevice(
                        mapOf(
                            ownUserId to mapOf(
                                requestingDeviceId to olmEvents.encryptOlm(
                                    SecretKeySendEventContent(
                                        request.requestId, requestedSecret.decryptedPrivateKey
                                    ), ownUserId, requestingDeviceId
                                )
                            )
                        )
                    ).getOrThrow()
                } else log.info { "got a key request (${request.name}) from $requestingDeviceId, but we do not have that secret cached" }
            }
            incomingSecretKeyRequests.update { it - request }
        }
    }

    internal suspend fun handleOutgoingKeyRequestAnswer(event: IOlmService.DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeySendEventContent) {
            log.trace { "handle outgoing key request answer $content" }
            val (senderDeviceId, senderTrustLevel) = store.keys.getDeviceKeys(ownUserId)?.firstNotNullOfOrNull {
                if (it.value.value.get<Ed25519Key>()?.value == event.decrypted.senderKeys.get<Ed25519Key>()?.value)
                    it.key to it.value.trustLevel
                else null
            } ?: (null to null)
            if (senderDeviceId == null) {
                log.warn { "could not derive sender device id from keys ${event.decrypted.senderKeys}" }
                return
            }
            if (!(senderTrustLevel is CrossSigned && senderTrustLevel.verified || senderTrustLevel is Valid && senderTrustLevel.verified)) {
                log.warn { "received a key from $senderDeviceId, but we don't trust that device ($senderTrustLevel)" }
                return
            }
            val request = store.keys.allSecretKeyRequests.value
                .firstOrNull { it.content.requestId == content.requestId }
            if (request?.receiverDeviceIds?.contains(senderDeviceId) != true) {
                log.warn { "received a key from $senderDeviceId, that we did not requested (or request is too old and we already deleted it)" }
                return
            }

            val secretType = request.content.name?.let { AllowedSecretType.ofId(it) }
            val publicKeyMatches = when (secretType) {
                M_CROSS_SIGNING_USER_SIGNING, M_CROSS_SIGNING_SELF_SIGNING -> {
                    val generatedPublicKey = try {
                        freeAfter(OlmPkSigning.create(content.secret)) { it.publicKey }
                    } catch (error: Throwable) {
                        log.warn(error) { "could not generate public key from received secret" }
                        return
                    }
                    val crossSigningKeyType =
                        if (secretType == M_CROSS_SIGNING_SELF_SIGNING) SelfSigningKey else UserSigningKey
                    val originalPublicKey = store.keys.getCrossSigningKey(ownUserId, crossSigningKeyType)
                        ?.value?.signed?.get<Ed25519Key>()?.value
                    originalPublicKey != null && originalPublicKey == generatedPublicKey
                }
                M_MEGOLM_BACKUP_V1 -> {
                    api.keys.getRoomKeysVersion().map {
                        keyBackup.keyBackupCanBeTrusted(it, content.secret)
                    }.onFailure { log.warn { "could not retrieve key backup version" } }
                        .getOrElse { false }
                }
                null -> false
            }
            if (secretType == null || !publicKeyMatches) {
                log.warn { "generated public key of secret ${request.content.name} did not match the original" }
                return
            }
            val encryptedSecret = secretType.getEncryptedSecret()
            if (encryptedSecret == null) {
                log.warn { "could not find encrypted secret" }
                return
            }
            store.keys.secrets.update {
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

    internal suspend fun cancelOldOutgoingKeyRequests() {
        store.keys.allSecretKeyRequests.value.forEach {
            if ((it.createdAt + 1.days) < Clock.System.now()) {
                cancelStoredSecretKeyRequest(it)
            }
        }
    }

    private suspend fun cancelStoredSecretKeyRequest(request: StoredSecretKeyRequest) {
        val cancelRequest = request.content.copy(action = KeyRequestAction.REQUEST_CANCELLATION)
        log.info { "cancel old outgoing key request $request" }
        api.users.sendToDevice(mapOf(ownUserId to request.receiverDeviceIds.associateWith { cancelRequest }))
            .onSuccess {
                store.keys.deleteSecretKeyRequest(cancelRequest.requestId)
            }.getOrThrow()
    }

    private suspend fun AllowedSecretType.getEncryptedSecret() = when (this) {
        M_CROSS_SIGNING_USER_SIGNING -> store.globalAccountData.get<UserSigningKeyEventContent>()
        M_CROSS_SIGNING_SELF_SIGNING -> store.globalAccountData.get<SelfSigningKeyEventContent>()
        M_MEGOLM_BACKUP_V1 -> store.globalAccountData.get<MegolmBackupV1EventContent>()
    }

    internal suspend fun requestSecretKeys() {
        val missingSecrets = AllowedSecretType.values()
            .subtract(store.keys.secrets.value.keys)
            .subtract(store.keys.allSecretKeyRequests.value.mapNotNull { request ->
                request.content.name?.let { AllowedSecretType.ofId(it) }
            }.toSet())
        if (missingSecrets.isEmpty()) {
            log.debug { "there are no missing secrets or they are already requested" }
            return
        }
        val receiverDeviceIds = store.keys.getDeviceKeys(ownUserId)
            ?.filter { it.value.trustLevel == CrossSigned(true) }
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
                    store.keys.addSecretKeyRequest(
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
                store.keys.getDeviceKey(ownUserId, ownDeviceId, this).collect { deviceKeys ->
                    if (deviceKeys?.trustLevel == CrossSigned(true)) {
                        requestSecretKeys()
                    }
                }
            }
        }
    }

    internal suspend fun handleChangedSecrets(event: ClientEvent<out SecretEventContent>) {
        val secretType =
            api.eventContentSerializerMappings.globalAccountData.find { event.content.instanceOf(it.kClass) }
                ?.let { AllowedSecretType.ofId(it.type) }
        if (secretType != null) {
            val storedSecret = store.keys.secrets.value[secretType]
            if (storedSecret?.event != event) {
                store.keys.allSecretKeyRequests.value.filter { it.content.name == secretType.id }
                    .forEach { cancelStoredSecretKeyRequest(it) }
                store.keys.secrets.update { it - secretType }
            }
        }
    }

    override suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    ) {
        val decryptedSecrets = AllowedSecretType.values()
            .subtract(store.keys.secrets.value.keys)
            .mapNotNull { allowedSecret ->
                val event = allowedSecret.getEncryptedSecret()
                if (event != null) {
                    decryptSecret(key, keyId, keyInfo, allowedSecret.id, event.content, api.json)
                        ?.let { allowedSecret to StoredSecret(event, it) }
                } else {
                    log.warn { "could not find secret ${allowedSecret.id} to decrypt and cache" }
                    null
                }
            }.toMap()
        store.keys.secrets.update {
            it + decryptedSecrets
        }
    }
}