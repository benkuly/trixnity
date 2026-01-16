package de.connect2x.trixnity.client.store.cache

import de.connect2x.trixnity.client.store.repository.FullRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager

internal class FullRepositoryObservableCacheStore<K, V>(
    private val repository: FullRepository<K, V>,
    private val tm: RepositoryTransactionManager,
) : MinimalRepositoryObservableCacheStore<K, V>(repository, tm) {
    suspend fun getAll() = tm.readTransaction { repository.getAll() }
}