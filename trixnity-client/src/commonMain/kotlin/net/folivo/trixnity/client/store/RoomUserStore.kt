package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryCoroutineCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership

class RoomUserStore(
    roomUserRepository: RoomUserRepository,
    tm: TransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope
) : Store {
    private val roomUserCache =
        MapDeleteByRoomIdRepositoryCoroutineCache(
            roomUserRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.roomUser
        ) { it.firstKey }

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        roomUserCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        roomUserCache.deleteByRoomId(roomId)
    }

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>?> =
        roomUserCache.readByFirstKey(roomId)

    fun get(userId: UserId, roomId: RoomId): Flow<RoomUser?> =
        roomUserCache.read(MapRepositoryCoroutinesCacheKey(roomId, userId))

    suspend fun update(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
    ) = roomUserCache.write(MapRepositoryCoroutinesCacheKey(roomId, userId), updater = updater)

    suspend fun getByOriginalNameAndMembership(
        originalName: String,
        membership: Set<Membership>,
        roomId: RoomId
    ): Set<UserId> {
        // TODO loading all users into memory could could make performance issues -> make db query
        return roomUserCache.readByFirstKey(roomId).first()
            ?.filter {
                val user = it.value.first()
                user?.originalName == originalName && membership.contains(user.membership)
            }?.keys
            ?: setOf()
    }
}