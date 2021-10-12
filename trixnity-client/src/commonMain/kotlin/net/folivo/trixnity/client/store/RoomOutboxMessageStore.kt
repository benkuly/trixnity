package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository

class RoomOutboxMessageStore(
    private val roomOutboxMessageRepository: RoomOutboxMessageRepository,
    storeScope: CoroutineScope
) {
    private val roomOutboxMessageCache = StateFlowCache(storeScope, roomOutboxMessageRepository, infiniteCache = true)

    @OptIn(FlowPreview::class)
    private val allRoomOutboxMessages =
        roomOutboxMessageCache.cache
            .flatMapMerge { combine(it.values) { transform -> transform } }
            .map { it.filterNotNull() }
            .stateIn(storeScope, Eagerly, listOf())

    suspend fun init() {
        roomOutboxMessageCache.init(roomOutboxMessageRepository.getAll().associateBy { it.transactionId })
    }

    fun getAll(): StateFlow<List<RoomOutboxMessage>> = allRoomOutboxMessages

    suspend fun add(message: RoomOutboxMessage) = roomOutboxMessageCache.update(message.transactionId) { message }

    suspend fun deleteByTransactionId(transactionId: String) = roomOutboxMessageCache.update(transactionId) { null }

    suspend fun markAsSent(transactionId: String) =
        roomOutboxMessageCache.update(transactionId) {
            it?.copy(wasSent = true)
        }
}