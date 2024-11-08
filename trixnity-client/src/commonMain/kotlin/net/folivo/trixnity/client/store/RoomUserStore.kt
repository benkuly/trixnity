package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomUserReceiptsRepository
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

class RoomUserStore(
    roomUserRepository: RoomUserRepository,
    roomUserReceiptsRepository: RoomUserReceiptsRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope
) : Store {
    private val roomUserCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            roomUserRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.roomUser
        ) { it.firstKey }.also(statisticCollector::addCache)

    private val roomUserReceiptsCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            roomUserReceiptsRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.roomUserReceipts
        ) { it.firstKey }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        roomUserCache.deleteAll()
        roomUserReceiptsCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        roomUserCache.deleteByRoomId(roomId)
        roomUserReceiptsCache.deleteByRoomId(roomId)
    }

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> =
        roomUserCache.readByFirstKey(roomId)

    fun get(userId: UserId, roomId: RoomId): Flow<RoomUser?> =
        roomUserCache.read(MapRepositoryCoroutinesCacheKey(roomId, userId))

    fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>> =
        roomUserReceiptsCache.readByFirstKey(roomId)

    fun getReceipts(userId: UserId, roomId: RoomId): Flow<RoomUserReceipts?> =
        roomUserReceiptsCache.read(MapRepositoryCoroutinesCacheKey(roomId, userId))

    suspend fun update(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
    ) = roomUserCache.write(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)

    suspend fun updateReceipts(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUserReceipts?) -> RoomUserReceipts?
    ) = roomUserReceiptsCache.write(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)
}