package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal open class MinimalRepositoryObservableCache<K, V>(
    repository: MinimalRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentMap(),
) : ObservableCache<K, V, ObservableCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MinimalRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    values = values,
)