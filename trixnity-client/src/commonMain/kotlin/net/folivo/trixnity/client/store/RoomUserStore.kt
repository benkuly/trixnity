package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership

class RoomUserStore(
    private val roomUserRepository: RoomUserRepository,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope
) : Store {
    private val roomUserCache =
        TwoDimensionsRepositoryStateFlowCache(storeScope, roomUserRepository, tm, config.cacheExpireDurations.roomUser)

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        tm.writeOperation { roomUserRepository.deleteAll() }
        roomUserCache.reset()
    }

    fun getAll(roomId: RoomId): Flow<Set<RoomUser>?> =
        roomUserCache.get(roomId).map { it?.values?.toSet() }

    fun get(userId: UserId, roomId: RoomId): Flow<RoomUser?> =
        roomUserCache.getBySecondKey(roomId, userId)

    suspend fun update(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
    ) = roomUserCache.updateBySecondKey(roomId, userId, updater)

    suspend fun getByOriginalNameAndMembership(
        originalName: String,
        membership: Set<Membership>,
        roomId: RoomId
    ): Set<UserId> {
        // TODO loading all users into memory could could make performance issues -> make db query
        return roomUserCache.get(roomId).first()
            ?.filter { it.value.originalName == originalName && membership.contains(it.value.membership) }?.keys
            ?: setOf()
    }
}