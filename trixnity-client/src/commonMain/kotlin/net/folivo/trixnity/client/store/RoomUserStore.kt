package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

class RoomUserStore(
    private val roomUserRepository: RoomUserRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) {
    private val roomUserCache = RepositoryStateFlowCache(storeScope, roomUserRepository, rtm)

    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> =
        roomUserCache.get(roomId, scope).map { it?.values?.toSet() }.stateIn(scope)

    suspend fun getAll(roomId: RoomId): Set<RoomUser>? =
        roomUserCache.get(roomId)?.values?.toSet()

    private fun containsInCache(userId: UserId, value: Map<UserId, RoomUser>?): Boolean {
        return value?.containsKey(userId) ?: false
    }

    private suspend fun retrieveFromRepoAndUpdateCache(
        userId: UserId,
        roomId: RoomId,
        cacheValue: Map<UserId, RoomUser>?
    ): Map<UserId, RoomUser>? {
        val roomUser = rtm.transaction { roomUserRepository.getByUserId(userId, roomId) }
        return if (roomUser != null) cacheValue?.plus(userId to roomUser) ?: mapOf(userId to roomUser)
        else cacheValue
    }

    private suspend fun getAsFlow(userId: UserId, roomId: RoomId, scope: CoroutineScope? = null): Flow<RoomUser?> {
        return roomUserCache.readWithCache(
            roomId,
            containsInCache = { containsInCache(userId, it) },
            retrieveAndUpdateCache = { cacheValue ->
                retrieveFromRepoAndUpdateCache(userId, roomId, cacheValue)
            },
            scope
        ).map { it?.get(userId) }
    }

    suspend fun get(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> {
        return getAsFlow(userId, roomId, scope).stateIn(scope)
    }

    suspend fun get(userId: UserId, roomId: RoomId): RoomUser? {
        return getAsFlow(userId, roomId).firstOrNull()
    }

    suspend fun update(
        userId: UserId,
        roomId: RoomId,
        updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
    ) {
        roomUserCache.writeWithCache(roomId,
            updater = {
                val newRoomUser = updater(it?.get(userId))
                if (newRoomUser != null) it?.plus(userId to newRoomUser) ?: mapOf(userId to newRoomUser)
                else it?.minus(userId)
            },
            containsInCache = { containsInCache(userId, it) },
            retrieveAndUpdateCache = { cacheValue ->
                retrieveFromRepoAndUpdateCache(userId, roomId, cacheValue)
            },
            persist = { newValue ->
                rtm.transaction {
                    val roomUser = newValue?.get(userId)
                    if (roomUser == null) roomUserRepository.deleteByUserId(userId, roomId)
                    else roomUserRepository.saveByUserId(userId, roomId, roomUser)
                }
            })
    }

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