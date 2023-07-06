package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager

open class MinimalRepositoryCoroutineCacheStore<K, V>(
    private val repository: MinimalRepository<K, V>,
    private val tm: TransactionManager,
) : CoroutineCacheStore<K, V> {
    override suspend fun get(key: K): V? = tm.readOperation { repository.get(key) }
    override suspend fun persist(key: K, value: V?): StateFlow<Boolean>? =
        tm.writeOperationAsync(repository::class.simpleName + repository.serializeKey(key)) {
            if (value == null) repository.delete(key)
            else repository.save(key, value)
        }

    override suspend fun deleteAll() {
        tm.writeOperation {
            repository.deleteAll()
        }
    }
}