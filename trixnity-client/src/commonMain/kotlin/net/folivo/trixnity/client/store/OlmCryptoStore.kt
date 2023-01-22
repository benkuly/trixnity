package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

class OlmCryptoStore(
    private val olmAccountRepository: OlmAccountRepository,
    private val olmForgetFallbackKeyAfterRepository: OlmForgetFallbackKeyAfterRepository,
    private val olmSessionRepository: OlmSessionRepository,
    private val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    private val inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    private val outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    private val storeScope: CoroutineScope
) : Store {
    val account = MutableStateFlow<String?>(null)
    val forgetFallbackKeyAfter = MutableStateFlow<Instant?>(null)

    private val _notBackedUpInboundMegolmSessions =
        MutableStateFlow<Map<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>>(mapOf())

    val notBackedUpInboundMegolmSessions = _notBackedUpInboundMegolmSessions.asStateFlow()

    override suspend fun init() {
        account.value = tm.readOperation { olmAccountRepository.get(1) }
        forgetFallbackKeyAfter.value = tm.readOperation { olmForgetFallbackKeyAfterRepository.get(1) }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            account.collect {
                tm.writeOperationAsync(olmAccountRepository.serializeKey(1)) {
                    if (it != null) olmAccountRepository.save(1, it)
                    else olmAccountRepository.delete(1)
                }
            }
        }
        storeScope.launch(start = UNDISPATCHED) {
            forgetFallbackKeyAfter.collect {
                tm.writeOperationAsync(olmForgetFallbackKeyAfterRepository.serializeKey(1)) {
                    if (it != null) olmForgetFallbackKeyAfterRepository.save(1, it)
                    else olmForgetFallbackKeyAfterRepository.delete(1)
                }
            }
        }
        storeScope.launch(start = UNDISPATCHED) {
            _notBackedUpInboundMegolmSessions.value =
                tm.readOperation { inboundMegolmSessionRepository.getByNotBackedUp() }
                    .associateBy { InboundMegolmSessionRepositoryKey(it.sessionId, it.roomId) }
        }
    }

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        tm.writeOperation {
            olmAccountRepository.deleteAll()
            olmForgetFallbackKeyAfterRepository.deleteAll()
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

    private val olmSessionsCache =
        MinimalRepositoryStateFlowCache(storeScope, olmSessionRepository, tm, config.cacheExpireDurations.olmSession)

    suspend fun updateOlmSessions(
        senderKey: Curve25519Key,
        updater: suspend (oldSessions: Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmSessionsCache.update(senderKey, updater = updater)

    private val inboundMegolmSessionCache =
        MinimalRepositoryStateFlowCache(
            storeScope,
            inboundMegolmSessionRepository,
            tm,
            config.cacheExpireDurations.inboundMegolmSession
        )

    fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): Flow<StoredInboundMegolmSession?> =
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
        MinimalRepositoryStateFlowCache(
            storeScope,
            inboundMegolmMessageIndexRepository,
            tm,
            config.cacheExpireDurations.inboundMegolmMessageIndex
        )

    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (oldMegolmSessionIndex: StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = inboundMegolmSessionIndexCache.update(
        InboundMegolmMessageIndexRepositoryKey(sessionId, roomId, messageIndex), updater = updater
    )

    private val outboundMegolmSessionCache =
        MinimalRepositoryStateFlowCache(
            storeScope,
            outboundMegolmSessionRepository,
            tm,
            config.cacheExpireDurations.outboundMegolmSession
        )

    suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSessionCache.get(roomId).first()

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (oldOutboundMegolmSession: StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = outboundMegolmSessionCache.update(roomId, updater = updater)
}