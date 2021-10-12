package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key

class OlmStore(
    private val olmAccountRepository: OlmAccountRepository,
    olmSessionRepository: OlmSessionRepository,
    inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    private val storeScope: CoroutineScope
) {
    val account: MutableStateFlow<String?> = MutableStateFlow(null)

    suspend fun init() {
        account.value = olmAccountRepository.get(1)
        storeScope.launch {
            account.collect {
                if (it != null) olmAccountRepository.save(1, it)
                else olmAccountRepository.delete(1)
            }
        }
    }

    private val olmSessionsCache = StateFlowCache(storeScope, olmSessionRepository)

    suspend fun getOlmSessions(
        senderKey: Curve25519Key
    ): Set<StoredOlmSession>? = olmSessionsCache.get(senderKey).value

    suspend fun updateOlmSessions(
        senderKey: Curve25519Key,
        updater: suspend (oldSessions: Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmSessionsCache.update(senderKey, updater)

    private val inboundMegolmSessionCache = StateFlowCache(storeScope, inboundMegolmSessionRepository)

    suspend fun getInboundMegolmSession(
        senderKey: Curve25519Key,
        sessionId: String,
        roomId: RoomId,
        scope: CoroutineScope
    ): StateFlow<StoredInboundMegolmSession?> =
        inboundMegolmSessionCache.get(InboundMegolmSessionRepositoryKey(senderKey, sessionId, roomId), scope)

    suspend fun updateInboundMegolmSession(
        senderKey: Curve25519Key,
        sessionId: String,
        roomId: RoomId,
        updater: suspend (oldInboundMegolmSession: StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = inboundMegolmSessionCache.update(InboundMegolmSessionRepositoryKey(senderKey, sessionId, roomId), updater)

    private val inboundMegolmSessionIndexCache = StateFlowCache(storeScope, inboundMegolmMessageIndexRepository)

    suspend fun updateInboundMegolmMessageIndex(
        senderKey: Curve25519Key,
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (oldMegolmSessionIndex: StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = inboundMegolmSessionIndexCache.update(
        InboundMegolmMessageIndexRepositoryKey(senderKey, sessionId, roomId, messageIndex), updater
    )

    private val outboundMegolmSessionCache = StateFlowCache(storeScope, outboundMegolmSessionRepository)

    suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSessionCache.get(roomId).value

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (oldOutboundMegolmSession: StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = outboundMegolmSessionCache.update(roomId, updater)
}