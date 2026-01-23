package de.connect2x.trixnity.client.key

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.isVerified
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.*
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.KeyRequestAction
import de.connect2x.trixnity.core.model.events.m.RoomKeyRequestEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSession
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.OlmDecrypter
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

private val log = Logger("de.connect2x.trixnity.client.key.IncomingRoomKeyRequestEventHandler")

class IncomingRoomKeyRequestEventHandler(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    private val accountStore: AccountStore,
    private val keyStore: KeyStore,
    private val olmStore: OlmCryptoStore,
    private val driver: CryptoDriver,
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
                    val account = checkNotNull(accountStore.getAccount()) { "No account found" }
                    val session = driver.megolm.inboundGroupSession.fromPickle(
                        foundInboundMegolmSession.pickled, driver.key.pickleKey(account.olmPickleKey)
                    ).use(InboundGroupSession::exportAtFirstKnownIndex)
                    val encryptedAnswer =
                        olmEncryptionService.encryptOlm(
                            ForwardedRoomKeyEventContent(
                                roomId = foundInboundMegolmSession.roomId,
                                senderKey = foundInboundMegolmSession.senderKey,
                                sessionId = foundInboundMegolmSession.sessionId,
                                sessionKey = ExportedSessionKeyValue.of(session),
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