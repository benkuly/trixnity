package net.folivo.trixnity.client.store.cache

import net.folivo.trixnity.client.store.repository.FullRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

internal class FullRepositoryCoroutineCacheStore<K, V>(
    private val repository: FullRepository<K, V>,
    private val tm: RepositoryTransactionManager,
) : MinimalRepositoryCoroutineCacheStore<K, V>(repository, tm) {
    suspend fun getAll() = tm.readTransaction { repository.getAll() }
}