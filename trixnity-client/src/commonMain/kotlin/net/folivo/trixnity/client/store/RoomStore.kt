package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration

class RoomStore(
    roomRepository: RoomRepository,
    tm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomCache =
        FullRepositoryObservableCache(roomRepository, tm, storeScope, Duration.INFINITE) { it.roomId }

    override suspend fun init() {
        roomCache.fillWithValuesFromRepository()
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomCache.deleteAll()
    }

    private val allRooms =
        roomCache.values.stateIn(storeScope, SharingStarted.Eagerly, mapOf())

    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> = allRooms

    fun get(roomId: RoomId): Flow<Room?> = roomCache.read(roomId)

    suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.write(roomId, updater = updater)

    suspend fun delete(roomId: RoomId) =
        roomCache.write(roomId, null)
}