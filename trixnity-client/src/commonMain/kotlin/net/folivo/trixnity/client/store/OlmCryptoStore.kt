package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlin.time.Duration

class OlmCryptoStore(
    olmAccountRepository: OlmAccountRepository,
    olmForgetFallbackKeyAfterRepository: OlmForgetFallbackKeyAfterRepository,
    olmSessionRepository: OlmSessionRepository,
    private val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    private val tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    private val storeScope: CoroutineScope
) : Store {
    private val olmAccountCache =
        MinimalRepositoryObservableCache(olmAccountRepository, tm, storeScope, Duration.INFINITE)
            .also(statisticCollector::addCache)
    private val olmForgetFallbackKeyAfterCache =
        MinimalRepositoryObservableCache(olmForgetFallbackKeyAfterRepository, tm, storeScope, Duration.INFINITE)
            .also(statisticCollector::addCache)

    private val _notBackedUpInboundMegolmSessions =
        MutableStateFlow<Map<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>>(mapOf())

    val notBackedUpInboundMegolmSessions = _notBackedUpInboundMegolmSessions.asStateFlow()
    override suspend fun init(coroutineScope: CoroutineScope) {
        storeScope.launch(start = UNDISPATCHED) {
            _notBackedUpInboundMegolmSessions.value =
                tm.readTransaction { inboundMegolmSessionRepository.getByNotBackedUp() }
                    .associateBy { InboundMegolmSessionRepositoryKey(it.sessionId, it.roomId) }
        }
    }

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        _notBackedUpInboundMegolmSessions.value = mapOf()
        olmAccountCache.deleteAll()
        olmForgetFallbackKeyAfterCache.deleteAll()
        olmSessionsCache.deleteAll()
        inboundMegolmSessionCache.deleteAll()
        inboundMegolmSessionIndexCache.deleteAll()
        outboundMegolmSessionCache.deleteAll()
    }

    private val olmSessionsCache =
        MinimalRepositoryObservableCache(
            olmSessionRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.olmSession
        ).also(statisticCollector::addCache)

    suspend fun getOlmAccount() = olmAccountCache.read(1).first()
    suspend fun updateOlmAccount(updater: suspend (String?) -> String) = olmAccountCache.write(1) {
        updater(it)
    }

    suspend fun getForgetFallbackKeyAfter() = olmForgetFallbackKeyAfterCache.read(1)
    suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?) =
        olmForgetFallbackKeyAfterCache.write(1, updater = updater)

    suspend fun updateOlmSessions(
        senderKey: Curve25519Key,
        updater: suspend (oldSessions: Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmSessionsCache.write(senderKey, updater = updater)

    private val inboundMegolmSessionCache =
        MinimalRepositoryObservableCache(
            inboundMegolmSessionRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.inboundMegolmSession
        ).also(statisticCollector::addCache)

    fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): Flow<StoredInboundMegolmSession?> =
        inboundMegolmSessionCache.read(InboundMegolmSessionRepositoryKey(sessionId, roomId))

    suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (oldInboundMegolmSession: StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = inboundMegolmSessionCache.write(
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
        MinimalRepositoryObservableCache(
            inboundMegolmMessageIndexRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.inboundMegolmMessageIndex
        ).also(statisticCollector::addCache)

    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (oldMegolmSessionIndex: StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = inboundMegolmSessionIndexCache.write(
        InboundMegolmMessageIndexRepositoryKey(sessionId, roomId, messageIndex), updater = updater
    )

    private val outboundMegolmSessionCache =
        MinimalRepositoryObservableCache(
            outboundMegolmSessionRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.outboundMegolmSession
        ).also(statisticCollector::addCache)

    suspend fun getOutboundMegolmSession(roomId: RoomId): StoredOutboundMegolmSession? =
        outboundMegolmSessionCache.read(roomId).first()

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (oldOutboundMegolmSession: StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = outboundMegolmSessionCache.write(roomId, updater = updater)
}