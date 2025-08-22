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
import kotlin.time.Clock

class RoomUserStore(
    roomUserRepository: RoomUserRepository,
    roomUserReceiptsRepository: RoomUserReceiptsRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomUserCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            roomUserRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.roomUser
        ) { it.firstKey }.also(statisticCollector::addCache)

    private val roomUserReceiptsCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            roomUserReceiptsRepository,
            tm,
            storeScope,
            clock,
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
        roomUserCache.get(MapRepositoryCoroutinesCacheKey(roomId, userId))

    fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>> =
        roomUserReceiptsCache.readByFirstKey(roomId)

    fun getReceipts(userId: UserId, roomId: RoomId): Flow<RoomUserReceipts?> =
        roomUserReceiptsCache.get(MapRepositoryCoroutinesCacheKey(roomId, userId))

    suspend fun update(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
    ) = roomUserCache.update(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)

    suspend fun updateReceipts(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUserReceipts?) -> RoomUserReceipts?
    ) = roomUserReceiptsCache.update(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)
}