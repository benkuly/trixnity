package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class MinimalRepositoryCoroutineCache<K, V>(
    repository: MinimalRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
) : CoroutineCache<K, V, CoroutineCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MinimalRepositoryCoroutineCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration
)