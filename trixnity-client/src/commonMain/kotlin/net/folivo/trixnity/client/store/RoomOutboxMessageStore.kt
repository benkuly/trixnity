package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository

class RoomOutboxMessageStore(
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    tm: RepositoryTransactionManager,
    storeScope: CoroutineScope,
    config: MatrixClientConfiguration,
) : Store {
    private val roomOutboxMessageCache = FullRepositoryObservableCache(
        roomOutboxMessageRepository,
        tm,
        storeScope,
        config.cacheExpireDurations.roomOutboxMessage,
    ) { it.transactionId }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        roomOutboxMessageCache.deleteAll()
    }

    fun getAll(): Flow<Map<String, Flow<RoomOutboxMessage<*>?>>> = roomOutboxMessageCache.readAll()

    suspend fun update(transactionId: String, updater: suspend (RoomOutboxMessage<*>?) -> RoomOutboxMessage<*>?) =
        roomOutboxMessageCache.write(transactionId, updater = updater)

    suspend fun get(transactionId: String): RoomOutboxMessage<*>? =
        roomOutboxMessageCache.read(transactionId).first()

    fun getAsFlow(transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageCache.read(transactionId)
}