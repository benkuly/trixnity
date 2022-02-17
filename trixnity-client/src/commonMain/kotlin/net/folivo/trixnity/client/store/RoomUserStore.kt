package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership

class RoomUserStore(
    private val roomUserRepository: RoomUserRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) {
    private val roomUserCache = TwoDimensionsRepositoryStateFlowCache(storeScope, roomUserRepository, rtm)

    suspend fun deleteAll() {
        rtm.transaction { roomUserRepository.deleteAll() }
        roomUserCache.reset()
    }

    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> =
        roomUserCache.get(roomId, scope = scope).map { it?.values?.toSet() }.stateIn(scope)

    suspend fun getAll(roomId: RoomId): Set<RoomUser>? =
        roomUserCache.get(roomId)?.values?.toSet()

    suspend fun get(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> =
        roomUserCache.getBySecondKey(roomId, userId, scope)

    suspend fun get(userId: UserId, roomId: RoomId): RoomUser? = roomUserCache.getBySecondKey(roomId, userId)

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
        return roomUserCache.get(roomId)
            ?.filter { it.value.originalName == originalName && membership.contains(it.value.membership) }?.keys
            ?: setOf()
    }
}