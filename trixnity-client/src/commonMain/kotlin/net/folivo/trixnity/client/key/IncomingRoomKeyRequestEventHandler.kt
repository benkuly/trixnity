package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.isVerified
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.freeAfter

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.IncomingRoomKeyRequestEventHandler")

class IncomingRoomKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    private val accountStore: AccountStore,
    private val keyStore: KeyStore,
    private val olmStore: OlmCryptoStore,
) : EventHandler {
    private val ownUserId = userInfo.userId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleEncryptedIncomingKeyRequests).unsubscribeOnCompletion(scope)
        api.sync.subscribeEvent(subscriber = ::handleIncomingKeyRequests).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::processIncomingKeyRequests).unsubscribeOnCompletion(scope)
    }

    private val incomingRoomKeyRequests = MutableStateFlow<Set<RoomKeyRequestEventContent>>(setOf())

    internal fun handleEncryptedIncomingKeyRequests(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is RoomKeyRequestEventContent) {
            handleIncomingKeyRequests(ToDeviceEvent(content, event.decrypted.sender))
        }
    }

    internal fun handleIncomingKeyRequests(event: ToDeviceEvent<RoomKeyRequestEventContent>) {
        if (event.sender == ownUserId) {
            log.debug { "handle incoming room key requests" }
            val content = event.content
            when (content.action) {
                KeyRequestAction.REQUEST -> incomingRoomKeyRequests.update { it + content }
                KeyRequestAction.REQUEST_CANCELLATION -> incomingRoomKeyRequests
                    .update { oldRequests -> oldRequests.filterNot { it.requestId == content.requestId }.toSet() }
            }
        }
    }

    internal suspend fun processIncomingKeyRequests() {
        incomingRoomKeyRequests.value.forEach { request ->
            val requestingDeviceId = request.requestingDeviceId
            val senderTrustLevel = keyStore.getDeviceKey(ownUserId, requestingDeviceId).first()?.trustLevel
            val requestBody = request.body
            log.debug { "process incoming room key request (requestId=${request.requestId}, isVerified=${senderTrustLevel?.isVerified})" }
            if (senderTrustLevel?.isVerified == true && requestBody != null && requestBody.algorithm == EncryptionAlgorithm.Megolm) {
                val foundInboundMegolmSession =
                    olmStore.getInboundMegolmSession(requestBody.sessionId, requestBody.roomId).first()
                if (foundInboundMegolmSession != null) {
                    log.info { "send incoming room key request answer (${request.requestId}) to device $requestingDeviceId" }
                    val encryptedAnswer =
                        olmEncryptionService.encryptOlm(
                            ForwardedRoomKeyEventContent(
                                roomId = foundInboundMegolmSession.roomId,
                                senderKey = foundInboundMegolmSession.senderKey,
                                sessionId = foundInboundMegolmSession.sessionId,
                                sessionKey = freeAfter(
                                    OlmInboundGroupSession.unpickle(
                                        checkNotNull(accountStore.getAccount()?.olmPickleKey),
                                        foundInboundMegolmSession.pickled
                                    )
                                ) { it.export(it.firstKnownIndex) },
                                senderClaimedKey = foundInboundMegolmSession.senderSigningKey,
                                forwardingKeyChain = foundInboundMegolmSession.forwardingCurve25519KeyChain,
                                algorithm = EncryptionAlgorithm.Megolm,
                            ),
                            ownUserId, requestingDeviceId
                        ).getOrNull()
                    if (encryptedAnswer != null)
                        api.user.sendToDevice(
                            mapOf(ownUserId to mapOf(requestingDeviceId to encryptedAnswer))
                        ).getOrThrow()
                } else log.info { "got a room key request (${request}), but did not found a matching room key" }
            }
            incomingRoomKeyRequests.update { it - request }
        }
    }
}