package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId

class RoomStore(
    private val roomRepository: RoomRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) {
    private val roomCache = RepositoryStateFlowCache(storeScope, roomRepository, rtm, infiniteCache = true)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val allRooms =
        roomCache.cache
            .flatMapLatest {
                if (it.isEmpty()) flowOf(arrayOf())
                else combine(it.values) { transform -> transform }
            }
            .mapLatest { it.filterNotNull().toSet() }
            .stateIn(storeScope, Eagerly, setOf())

    suspend fun init() {
        roomCache.init(rtm.transaction { roomRepository.getAll() }.associateBy { it.roomId })
    }

    fun getAll(): StateFlow<Set<Room>> = allRooms

    suspend fun get(roomId: RoomId): StateFlow<Room?> = roomCache.getWithInfiniteMode(roomId)

    suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?) =
        roomCache.update(roomId, updater = updater)
}