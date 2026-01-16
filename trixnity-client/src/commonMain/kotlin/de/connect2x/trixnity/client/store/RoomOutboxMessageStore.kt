package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.FullDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import kotlin.time.Clock

class RoomOutboxMessageStore(
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomOutboxMessageCache = FullDeleteByRoomIdRepositoryObservableCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.roomOutboxMessage,
        { RoomOutboxMessageRepositoryKey(it.roomId, it.transactionId) }) {
        it.roomId
    }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomOutboxMessageCache.deleteAll()
    }

    fun getAll(): Flow<Map<RoomOutboxMessageRepositoryKey, Flow<RoomOutboxMessage<*>?>>> =
        roomOutboxMessageCache.readAll()

    suspend fun update(
        roomId: RoomId,
        transactionId: String,
        updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?
    ) =
        roomOutboxMessageCache.update(RoomOutboxMessageRepositoryKey(roomId, transactionId), updater = updater)

    fun get(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.get(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    fun getAsFlow(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.get(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    suspend fun deleteByRoomId(roomId: RoomId) = roomOutboxMessageCache.deleteByRoomId(roomId)
}