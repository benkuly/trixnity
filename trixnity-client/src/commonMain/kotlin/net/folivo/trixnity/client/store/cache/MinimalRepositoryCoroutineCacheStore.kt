package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

open class MinimalRepositoryCoroutineCacheStore<K, V>(
    private val repository: MinimalRepository<K, V>,
    private val tm: RepositoryTransactionManager,
) : CoroutineCacheStore<K, V> {
    override suspend fun get(key: K): V? = tm.readTransaction { repository.get(key) }
    override suspend fun persist(key: K, value: V?) =
        tm.writeTransaction {
            if (value == null) repository.delete(key)
            else repository.save(key, value)
        }

    override suspend fun deleteAll() {
        tm.writeTransaction {
            repository.deleteAll()
        }
    }
}