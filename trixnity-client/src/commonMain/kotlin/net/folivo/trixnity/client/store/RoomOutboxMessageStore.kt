package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import kotlin.time.Duration

class RoomOutboxMessageStore(
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    tm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val roomOutboxMessageCache = FullRepositoryObservableCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
        Duration.INFINITE
    ) { it.transactionId }

    override suspend fun init() {
        roomOutboxMessageCache.fillWithValuesFromRepository()
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomOutboxMessageCache.deleteAll()
    }

    private val allRoomOutboxMessages =
        roomOutboxMessageCache.values.stateIn(storeScope, Eagerly, mapOf())

    fun getAll(): StateFlow<Map<String, StateFlow<RoomOutboxMessage<*>?>>> = allRoomOutboxMessages

    suspend fun update(transactionId: String, updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?) =
        roomOutboxMessageCache.write(transactionId, updater = updater)

    suspend fun get(transactionId: String): RoomOutboxMessage<*>? =
        roomOutboxMessageCache.read(transactionId).first()
}