package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

class RoomUserStore(
    roomUserRepository: RoomUserRepository,
    storeScope: CoroutineScope
) {
    private val roomUserCache = StateFlowCache(storeScope, roomUserRepository)

    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> =
        roomUserCache.get(roomId, scope).map { it?.values?.toSet() }.stateIn(scope)

    private fun containsInCache(userId: UserId, value: Map<UserId, RoomUser>?): Boolean {
        return value?.containsKey(userId) ?: false
    }

    private suspend fun retrieveFromRepoAndUpdateCache(
        userId: UserId,
        roomId: RoomId,
        cacheValue: Map<UserId, RoomUser>?,
        repository: RoomUserRepository
    ): Map<UserId, RoomUser>? {
        val roomUser = repository.getByUserId(userId, roomId)
        return if (roomUser != null) cacheValue?.plus(userId to roomUser) ?: mapOf(userId to roomUser)
        else cacheValue
    }

    private suspend fun getAsFlow(userId: UserId, roomId: RoomId, scope: CoroutineScope? = null): Flow<RoomUser?> {
        return roomUserCache.readWithCache(
            roomId,
            containsInCache = { containsInCache(userId, it) },
            retrieveFromRepoAndUpdateCache = { cacheValue, repository ->
                retrieveFromRepoAndUpdateCache(userId, roomId, cacheValue, repository)
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
            getFromRepositoryAndUpdateCache = { cacheValue, repository ->
                retrieveFromRepoAndUpdateCache(userId, roomId, cacheValue, repository)
            },
            persistIntoRepository = { newValue, repository ->
                val roomUser = newValue?.get(userId)
                if (roomUser == null) repository.deleteByUserId(userId, roomId)
                else repository.saveByUserId(userId, roomId, roomUser)
            })
    }

    suspend fun getByOriginalNameAndMembership(
        originalName: String,
        membership: Set<Membership>,
        roomId: RoomId
    ): Set<UserId> {
        // TODO loading all users into memory could could make performance issues -> make db query
        return roomUserCache.get(roomId).value?.filter { it.value.originalName == originalName && membership.contains(it.value.membership) }?.keys
            ?: setOf()
    }
}