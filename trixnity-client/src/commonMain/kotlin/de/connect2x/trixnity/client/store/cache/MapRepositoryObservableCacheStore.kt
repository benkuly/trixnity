package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.MapRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager

internal class MapRepositoryObservableCacheStore<K1, K2, V>(
    private val repository: MapRepository<K1, K2, V>,
    private val tm: RepositoryTransactionManager,
) : ObservableCacheStore<MapRepositoryCoroutinesCacheKey<K1, K2>, V> {
    override suspend fun get(key: MapRepositoryCoroutinesCacheKey<K1, K2>): V? =
        tm.readTransaction { repository.get(key.firstKey, key.secondKey) }

    suspend fun getByFirstKey(key: K1): Map<K2, V> =
        tm.readTransaction { repository.get(key) }

    override suspend fun persist(key: MapRepositoryCoroutinesCacheKey<K1, K2>, value: V?) =
        tm.writeTransaction {
            if (value == null) repository.delete(key.firstKey, key.secondKey)
            else repository.save(key.firstKey, key.secondKey, value)
        }

    override suspend fun deleteAll() {
        tm.writeTransaction {
            repository.deleteAll()
        }
    }
}