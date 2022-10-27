package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

private val log = KotlinLogging.logger {}

class IncomingKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    private val keyStore: KeyStore,
) : EventHandler {
    private val ownUserId = userInfo.userId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleEncryptedIncomingKeyRequests)
        api.sync.subscribeAfterSyncResponse(::processIncomingKeyRequests)
        api.sync.subscribe(::handleIncomingKeyRequests)
        scope.coroutineContext.job.invokeOnCompletion {
            olmDecrypter.unsubscribe(::handleEncryptedIncomingKeyRequests)
            api.sync.unsubscribeAfterSyncResponse(::processIncomingKeyRequests)
            api.sync.unsubscribe(::handleIncomingKeyRequests)
        }
    }

    private val incomingSecretKeyRequests = MutableStateFlow<Set<SecretKeyRequestEventContent>>(setOf())

    internal fun handleEncryptedIncomingKeyRequests(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeyRequestEventContent) {
            handleIncomingKeyRequests(Event.ToDeviceEvent(content, event.decrypted.sender))
        }
    }

    internal fun handleIncomingKeyRequests(event: Event<SecretKeyRequestEventContent>) {
        if (event is Event.ToDeviceEvent && event.sender == ownUserId) {
            log.debug { "handle incoming key requests" }
            val content = event.content
            when (content.action) {
                KeyRequestAction.REQUEST -> incomingSecretKeyRequests.update { it + content }
                KeyRequestAction.REQUEST_CANCELLATION -> incomingSecretKeyRequests
                    .update { oldRequests -> oldRequests.filterNot { it.requestId == content.requestId }.toSet() }
            }
        }
    }

    internal suspend fun processIncomingKeyRequests(syncResponse: Sync.Response) {
        incomingSecretKeyRequests.value.forEach { request ->
            log.debug { "process incoming key request: ${request.requestId}" }
            val requestingDeviceId = request.requestingDeviceId
            val senderTrustLevel = keyStore.getDeviceKey(ownUserId, requestingDeviceId).first()?.trustLevel
            if (senderTrustLevel is KeySignatureTrustLevel.CrossSigned && senderTrustLevel.verified || senderTrustLevel is KeySignatureTrustLevel.Valid && senderTrustLevel.verified) {
                val requestedSecret = request.name
                    ?.let { SecretType.ofId(it) }
                    ?.let { keyStore.secrets.value[it] }
                if (requestedSecret != null) {
                    log.info { "send incoming key request answer (${request.name}) to device $requestingDeviceId" }
                    val encryptedAnswer = try {
                        olmEncryptionService.encryptOlm(
                            SecretKeySendEventContent(request.requestId, requestedSecret.decryptedPrivateKey),
                            ownUserId, requestingDeviceId
                        )
                    } catch (exception: Exception) {
                        log.warn(exception) { "could not encrypt answer for key request (${request.name}) to device $requestingDeviceId" }
                        null
                    }
                    if (encryptedAnswer != null)
                        api.users.sendToDevice(
                            mapOf(ownUserId to mapOf(requestingDeviceId to encryptedAnswer))
                        ).getOrThrow()
                } else log.info { "got a key request (${request.name}) from $requestingDeviceId, but we do not have that secret cached" }
            }
            incomingSecretKeyRequests.update { it - request }
        }
    }
}