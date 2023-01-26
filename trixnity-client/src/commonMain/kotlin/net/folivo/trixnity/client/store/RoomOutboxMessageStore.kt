package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration

class RoomOutboxMessageStore(
    private val roomOutboxMessageRepository: RoomOutboxMessageRepository,
    private val tm: TransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomOutboxMessageCache = MinimalRepositoryStateFlowCache(
        storeScope, roomOutboxMessageRepository, tm = tm, Duration.INFINITE
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allRoomOutboxMessages =
        roomOutboxMessageCache.cache
            .flatMapLatest {
                if (it.isEmpty()) flowOf(arrayOf())
                else combine(it.values) { transform -> transform }
            }
            .mapLatest { it.filterNotNull() }
            .stateIn(storeScope, Eagerly, listOf())

    override suspend fun init() {
        roomOutboxMessageCache.init(tm.readOperation { roomOutboxMessageRepository.getAll() }
            .associateBy { it.transactionId })
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        tm.writeOperation {
            roomOutboxMessageRepository.deleteAll()
        }
        roomOutboxMessageCache.reset()
    }

    fun getAll(): StateFlow<List<RoomOutboxMessage<*>>> = allRoomOutboxMessages

    suspend fun update(transactionId: String, updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?) =
        roomOutboxMessageCache.update(transactionId, updater = updater)

    suspend fun get(transactionId: String): RoomOutboxMessage<*>? =
        roomOutboxMessageCache.get(transactionId).first()
}