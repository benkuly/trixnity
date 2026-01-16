package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.MinimalRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager

internal open class MinimalRepositoryObservableCacheStore<K, V>(
    private val repository: MinimalRepository<K, V>,
    private val tm: RepositoryTransactionManager,
) : ObservableCacheStore<K, V> {
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