package de.connect2x.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import de.connect2x.trixnity.client.store.repository.MinimalRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal open class MinimalRepositoryObservableCache<K : Any, V>(
    repository: MinimalRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
) : ObservableCache<K, V, ObservableCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MinimalRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    clock = clock,
    expireDuration = expireDuration,
    values = values,
)