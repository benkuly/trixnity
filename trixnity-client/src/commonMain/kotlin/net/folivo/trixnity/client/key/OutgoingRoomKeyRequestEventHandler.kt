package net.folivo.trixnity.client.key

import com.benasher44.uuid.uuid4
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.days

private val log = KotlinLogging.logger {}

interface OutgoingRoomKeyRequestEventHandler {
    suspend fun requestRoomKeys(
        roomId: RoomId,
        sessionId: String,
    )
}

class OutgoingRoomKeyRequestEventHandlerImpl(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val accountStore: AccountStore,
    private val keyStore: KeyStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val currentSyncState: CurrentSyncState,
) : EventHandler, OutgoingRoomKeyRequestEventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmDecrypter.subscribe(::handleOutgoingKeyRequestAnswer)
        api.sync.subscribeAfterSyncResponse(::cancelOldOutgoingKeyRequests)
        scope.coroutineContext.job.invokeOnCompletion {
            olmDecrypter.unsubscribe(::handleOutgoingKeyRequestAnswer)
            api.sync.unsubscribeAfterSyncResponse(::cancelOldOutgoingKeyRequests)
        }
    }

    internal suspend fun handleOutgoingKeyRequestAnswer(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is ForwardedRoomKeyEventContent) {
            log.debug { "handle forwarded room key (roomId=${content.roomId}, sessionId=${content.sessionId})" }
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

            val (firstKnownIndex, pickledSession) =
                try {
                    freeAfter(OlmInboundGroupSession.import(content.sessionKey)) {
                        it.firstKnownIndex to it.pickle(requireNotNull(accountStore.olmPickleKey.value))
                    }
                } catch (exception: Exception) {
                    log.warn(exception) { "could not import olm inbound session" }
                    return
                }
            val newForwardingCurve25519KeyChain = content.forwardingKeyChain + event.encrypted.content.senderKey
            olmCryptoStore.updateInboundMegolmSession(content.sessionId, content.roomId) {
                if (it != null && it.firstKnownIndex <= firstKnownIndex) it
                else StoredInboundMegolmSession(
                    senderKey = content.senderKey,
                    sessionId = content.sessionId,
                    roomId = content.roomId,
                    firstKnownIndex = firstKnownIndex,
                    isTrusted = false, // TODO we could add more trust, if we verify the key chain
                    hasBeenBackedUp = false, // actually not known if it has been backed up
                    senderSigningKey = content.senderClaimedKey,
                    forwardingCurve25519KeyChain = newForwardingCurve25519KeyChain,
                    pickled = pickledSession
                )
            }
            keyStore.allRoomKeyRequests.first()
                .find { request -> request.content.body?.roomId == content.roomId && request.content.body?.sessionId == content.sessionId }
                ?.cancelRequest()
        }
    }


    internal suspend fun cancelOldOutgoingKeyRequests(syncResponse: Sync.Response) {
        keyStore.allRoomKeyRequests.value.forEach {
            if ((it.createdAt + 1.days) < Clock.System.now()) {
                it.cancelRequest()
            }
        }
    }

    private suspend fun StoredRoomKeyRequest.cancelRequest(answeredFrom: String? = null) {
        val cancelRequestTo = receiverDeviceIds - setOfNotNull(answeredFrom)
        log.debug { "cancel outgoing room key request to $cancelRequestTo" }
        if (cancelRequestTo.isNotEmpty()) {
            val cancelRequest = content.copy(action = KeyRequestAction.REQUEST_CANCELLATION, body = null)
            api.users.sendToDevice( // TODO should be encrypted (because this is meta data)
                mapOf(ownUserId to cancelRequestTo.associateWith { cancelRequest })
            ).getOrThrow()
        }
        keyStore.deleteRoomKeyRequest(content.requestId)
    }

    override suspend fun requestRoomKeys(
        roomId: RoomId,
        sessionId: String,
    ) {
        if (keyStore.allRoomKeyRequests.value.none { it.content.body?.roomId == roomId && it.content.body?.sessionId == sessionId }) {
            currentSyncState.retryWhenSyncIs(
                SyncState.RUNNING,
                onError = { log.warn(it) { "failed request room keys" } },
            ) {
                val receiverDeviceIds = keyStore.getDeviceKeys(ownUserId).first()
                    ?.filter { it.value.trustLevel.isVerified }
                    ?.map { it.value.value.signed.deviceId }?.minus(ownDeviceId)?.toSet()
                if (receiverDeviceIds.isNullOrEmpty()) {
                    log.debug { "there are no receivers, that we can request room keys from" }
                    return@retryWhenSyncIs
                }
                val requestId = uuid4().toString()
                val request = RoomKeyRequestEventContent(
                    action = KeyRequestAction.REQUEST,
                    requestingDeviceId = ownDeviceId,
                    requestId = requestId,
                    body = RoomKeyRequestEventContent.RequestedKeyInfo(
                        roomId = roomId,
                        sessionId = sessionId,
                        algorithm = EncryptionAlgorithm.Megolm,
                    )
                )
                log.debug { "send room key request (roomId=$roomId, sessionId=$sessionId) to $receiverDeviceIds" }
                // TODO should be encrypted (because this is meta data)
                api.users.sendToDevice(mapOf(ownUserId to receiverDeviceIds.associateWith { request }))
                    .onSuccess {
                        keyStore.addRoomKeyRequest(
                            StoredRoomKeyRequest(request, receiverDeviceIds, Clock.System.now())
                        )
                    }.getOrThrow()
            }
        }
        keyStore.allRoomKeyRequests
            .first { requests -> requests.none { it.content.body?.roomId == roomId && it.content.body?.sessionId == sessionId } }
    }
}