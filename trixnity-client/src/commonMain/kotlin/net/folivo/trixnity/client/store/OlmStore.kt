package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

class OlmStore(
    private val olmAccountRepository: OlmAccountRepository,
    private val olmSessionRepository: OlmSessionRepository,
    private val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    private val inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    private val outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    private val rtm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) {
    val account = MutableStateFlow<String?>(null)

    private val _notBackedUpInboundMegolmSessions =
        MutableStateFlow<Map<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>>(mapOf())

    val notBackedUpInboundMegolmSessions = _notBackedUpInboundMegolmSessions.asStateFlow()

    suspend fun init() {
        account.value = rtm.transaction { olmAccountRepository.get(1) }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            account.collect {
                rtm.transaction {
                    if (it != null) olmAccountRepository.save(1, it)
                    else olmAccountRepository.delete(1)
                }
            }
        }
        storeScope.launch(start = UNDISPATCHED) {
            _notBackedUpInboundMegolmSessions.value =
                rtm.transaction { inboundMegolmSessionRepository.getByNotBackedUp() }
                    .associateBy { InboundMegolmSessionRepositoryKey(it.sessionId, it.roomId) }
        }
    }

    suspend fun deleteAll() {
        rtm.transaction {
            olmAccountRepository.deleteAll()
            olmSessionRepository.deleteAll()
            inboundMegolmSessionRepository.deleteAll()
            inboundMegolmMessageIndexRepository.deleteAll()
            outboundMegolmSessionRepository.deleteAll()
        }
        account.value = null
        _notBackedUpInboundMegolmSessions.value = mapOf()
        olmSessionsCache.reset()
        inboundMegolmSessionCache.reset()
        inboundMegolmSessionIndexCache.reset()
        outboundMegolmSessionCache.reset()
    }

    private val olmSessionsCache = RepositoryStateFlowCache(storeScope, olmSessionRepository, rtm)

    suspend fun getOlmSessions(
        senderKey: Curve25519Key
    ): Set<StoredOlmSession>? = olmSessionsCache.get(senderKey)

    suspend fun updateOlmSessions(
        senderKey: Curve25519Key,
        updater: suspend (oldSessions: Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmSessionsCache.update(senderKey, updater = updater)

    private val inboundMegolmSessionCache = RepositoryStateFlowCache(storeScope, inboundMegolmSessionRepository, rtm)

    suspend fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        scope: CoroutineScope
    ): StateFlow<StoredInboundMegolmSession?> =
        inboundMegolmSessionCache.get(InboundMegolmSessionRepositoryKey(sessionId, roomId), scope = scope)

    suspend fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): StoredInboundMegolmSession? =
        inboundMegolmSessionCache.get(InboundMegolmSessionRepositoryKey(sessionId, roomId))

    suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (oldInboundMegolmSession: StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = inboundMegolmSessionCache.update(
        InboundMegolmSessionRepositoryKey(sessionId, roomId),
        updater = updater,
        onPersist = { newValue ->
            val key = InboundMegolmSessionRepositoryKey(sessionId, roomId)
            _notBackedUpInboundMegolmSessions.update {
                if (newValue == null || newValue.hasBeenBackedUp) it - key
                else it + (key to newValue)
            }
        }
    )

    private val inboundMegolmSessionIndexCache =
        RepositoryStateFlowCache(storeScope, inboundMegolmMessageIndexRepository, rtm)

    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (oldMegolmSessionIndex: StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = inboundMegolmSessionIndexCache.update(
        InboundMegolmMessageIndexRepositoryKey(sessionId, roomId, messageIndex), updater = updater
    )

    private val outboundMegolmSessionCache = RepositoryStateFlowCache(storeScope, outboundMegolmSessionRepository, rtm)

    suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSessionCache.get(roomId)

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (oldOutboundMegolmSession: StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = outboundMegolmSessionCache.update(roomId, updater = updater)
}