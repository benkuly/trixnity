package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Clock

class RoomStore(
    roomRepository: RoomRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomCache =
        FullRepositoryObservableCache(
            roomRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.room
        ) { it.roomId }
            .also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomCache.deleteAll()
    }

    fun getAll(): Flow<Map<RoomId, Flow<Room?>>> = roomCache.readAll()

    fun get(roomId: RoomId): Flow<Room?> = roomCache.get(roomId)

    suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.update(roomId, updater = updater)

    suspend fun delete(roomId: RoomId) =
        roomCache.set(roomId, null)
}