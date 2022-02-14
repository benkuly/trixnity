package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository

class RoomOutboxMessageStore(
    private val roomOutboxMessageRepository: RoomOutboxMessageRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) {
    private val roomOutboxMessageCache = RepositoryStateFlowCache(
        storeScope, roomOutboxMessageRepository, infiniteCache = true,
        rtm = rtm
    )

    suspend fun deleteAll() {
        rtm.transaction {
            roomOutboxMessageRepository.deleteAll()
        }
        roomOutboxMessageCache.reset()
    }

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val allRoomOutboxMessages =
        roomOutboxMessageCache.cache
            .flatMapLatest {
                if (it.isEmpty()) flowOf(arrayOf())
                else combine(it.values) { transform -> transform }
            }
            .mapLatest { it.filterNotNull() }
            .stateIn(storeScope, Eagerly, listOf())

    suspend fun init() {
        roomOutboxMessageCache.init(rtm.transaction { roomOutboxMessageRepository.getAll() }
            .associateBy { it.transactionId })
    }

    fun getAll(): StateFlow<List<RoomOutboxMessage<*>>> = allRoomOutboxMessages

    suspend fun update(transactionId: String, updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?) =
        roomOutboxMessageCache.update(transactionId, updater = updater)

    suspend fun get(transactionId: String): RoomOutboxMessage<*>? =
        roomOutboxMessageCache.get(transactionId)
}