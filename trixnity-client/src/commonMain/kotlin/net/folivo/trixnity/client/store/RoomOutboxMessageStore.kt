package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.store.cache.FullRepositoryCoroutineCache
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration

class RoomOutboxMessageStore(
    private val roomOutboxMessageRepository: RoomOutboxMessageRepository,
    private val tm: TransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomOutboxMessageCache = FullRepositoryCoroutineCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
        Duration.INFINITE
    ) { it.transactionId }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allRoomOutboxMessages =
        roomOutboxMessageCache.values
            .flatMapLatest {
                if (it.isEmpty()) flowOf(arrayOf())
                else combine(it.values) { transform -> transform }
            }
            .mapLatest { it.filterNotNull() }
            .stateIn(storeScope, Eagerly, listOf())

    override suspend fun init() {
        roomOutboxMessageCache.fill()
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomOutboxMessageCache.deleteAll()
    }

    fun getAll(): StateFlow<List<RoomOutboxMessage<*>>> = allRoomOutboxMessages

    suspend fun update(transactionId: String, updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?) =
        roomOutboxMessageCache.write(transactionId, updater = updater)

    suspend fun get(transactionId: String): RoomOutboxMessage<*>? =
        roomOutboxMessageCache.read(transactionId).first()
}