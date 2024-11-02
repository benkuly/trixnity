package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.FullDeleteByRoomIdRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.core.model.RoomId

class RoomOutboxMessageStore(
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
) : Store {
    private val roomOutboxMessageCache = FullDeleteByRoomIdRepositoryObservableCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
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
        roomOutboxMessageCache.write(RoomOutboxMessageRepositoryKey(roomId, transactionId), updater = updater)

    fun get(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.read(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    fun getAsFlow(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.read(RoomOutboxMessageRepositoryKey(roomId, transactionId))

    suspend fun deleteByRoomId(roomId: RoomId) = roomOutboxMessageCache.deleteByRoomId(roomId)
}