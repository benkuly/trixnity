package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId

class RoomStore(
    private val roomRepository: RoomRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomCache = RepositoryStateFlowCache(storeScope, roomRepository, rtm, infiniteCache = true)

    override suspend fun init() {
        roomCache.init(rtm.transaction { roomRepository.getAll() }.associateBy { it.roomId })
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        rtm.transaction {
            roomRepository.deleteAll()
        }
        roomCache.reset()
    }

    private val allRooms = roomCache.cache.stateIn(storeScope, SharingStarted.Eagerly, mapOf())

    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> = allRooms

    fun get(roomId: RoomId): Flow<Room?> = roomCache.readWithCache(
        roomId,
        isContainedInCache = { true },
        retrieveAndUpdateCache = { it },
    )

    suspend fun update(roomId: RoomId, withTransaction: Boolean = true, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.update(roomId, withTransaction = withTransaction, updater = updater)
}