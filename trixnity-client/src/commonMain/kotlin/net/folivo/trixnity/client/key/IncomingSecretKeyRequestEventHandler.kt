package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.isVerified
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.IncomingSecretKeyRequestEventHandler")

class IncomingSecretKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    private val keyStore: KeyStore,
) : EventHandler {
    private val ownUserId = userInfo.userId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleEncryptedIncomingKeyRequests).unsubscribeOnCompletion(scope)
        api.sync.subscribeEvent(subscriber = ::handleIncomingKeyRequests).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::processIncomingKeyRequests).unsubscribeOnCompletion(scope)
    }

    private val incomingSecretKeyRequests = MutableStateFlow<Set<SecretKeyRequestEventContent>>(setOf())

    internal fun handleEncryptedIncomingKeyRequests(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeyRequestEventContent) {
            handleIncomingKeyRequests(ToDeviceEvent(content, event.decrypted.sender))
        }
    }

    internal fun handleIncomingKeyRequests(event: ClientEvent<SecretKeyRequestEventContent>) {
        if (event is ToDeviceEvent && event.sender == ownUserId) {
            log.debug { "handle incoming secret key requests" }
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
            log.debug { "process incoming secret key request: ${request.requestId}" }
            val requestingDeviceId = request.requestingDeviceId
            val senderTrustLevel = keyStore.getDeviceKey(ownUserId, requestingDeviceId).first()?.trustLevel
            if (senderTrustLevel?.isVerified == true) {
                val requestedSecret = request.name
                    ?.let { SecretType.ofId(it) }
                    ?.takeIf { it.shareable } // FIXME test
                    ?.let { keyStore.getSecrets()[it] }
                if (requestedSecret != null) {
                    log.info { "send incoming secret key request answer (${request.name}) to device $requestingDeviceId" }
                    val encryptedAnswer =
                        olmEncryptionService.encryptOlm(
                            SecretKeySendEventContent(request.requestId, requestedSecret.decryptedPrivateKey),
                            ownUserId, requestingDeviceId
                        ).getOrNull()
                    if (encryptedAnswer != null)
                        api.user.sendToDevice(
                            mapOf(ownUserId to mapOf(requestingDeviceId to encryptedAnswer))
                        ).getOrThrow()
                } else log.info { "got a secret key request (${request.name}) from $requestingDeviceId, but we do not have that secret cached" }
            }
            incomingSecretKeyRequests.update { it - request }
        }
    }
}