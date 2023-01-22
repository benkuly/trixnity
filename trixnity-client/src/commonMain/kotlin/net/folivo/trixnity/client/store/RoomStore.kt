package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration

class RoomStore(
    private val roomRepository: RoomRepository,
    private val tm: TransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomCache = MinimalRepositoryStateFlowCache(storeScope, roomRepository, tm, Duration.INFINITE)

    override suspend fun init() {
        roomCache.init(tm.readOperation { roomRepository.getAll() }.associateBy { it.roomId })
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        tm.writeOperation {
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

    suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.update(roomId, updater = updater)
}