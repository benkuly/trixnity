package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.MatrixId.RoomId

class RoomStore(
    private val roomRepository: RoomRepository,
    storeScope: CoroutineScope
) {

    private val roomCache = StateFlowCache(storeScope, roomRepository, true)

    @OptIn(FlowPreview::class)
    private val allRooms =
        roomCache.cache
            .flatMapMerge { combine(it.values) { transform -> transform } }
            .map { it.filterNotNull().toSet() }
            .stateIn(storeScope, Eagerly, setOf())

    suspend fun init() {
        roomCache.init(roomRepository.getAll().associateBy { it.roomId })
    }

    fun getAll(): StateFlow<Set<Room>> = allRooms

    suspend fun get(roomId: RoomId): StateFlow<Room?> = roomCache.get(roomId, null)

    suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.update(roomId, updater)
}