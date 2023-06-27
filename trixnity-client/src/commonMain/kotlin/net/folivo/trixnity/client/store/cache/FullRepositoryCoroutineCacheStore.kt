package net.folivo.trixnity.client.store.cache

import net.folivo.trixnity.client.store.repository.FullRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager

class FullRepositoryCoroutineCacheStore<K, V>(
    private val repository: FullRepository<K, V>,
    private val tm: TransactionManager,
) : MinimalRepositoryCoroutineCacheStore<K, V>(repository, tm) {
    suspend fun getAll() = tm.readOperation { repository.getAll() }
}