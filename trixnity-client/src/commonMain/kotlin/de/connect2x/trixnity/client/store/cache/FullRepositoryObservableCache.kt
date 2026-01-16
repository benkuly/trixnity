package de.connect2x.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import de.connect2x.trixnity.client.store.repository.FullRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private class FullRepositoryObservableCacheIndex<K>(
    private val loadFromStore: suspend () -> Unit,
) : ObservableCacheIndex<K> {

    private val allKeys = ConcurrentObservableSet<K>()
    private val subscribers = MutableStateFlow(0)
    private val fullyLoadedFromRepository = MutableStateFlow(false)
    override suspend fun onPut(key: K) {
        allKeys.add(key)
    }

    override suspend fun onSkipPut(key: K) {
        fullyLoadedFromRepository.value = false
    }

    override suspend fun onRemove(key: K, stale: Boolean) {
        fullyLoadedFromRepository.update { it && stale }
        allKeys.remove(key)
    }

    override suspend fun onRemoveAll() {
        allKeys.removeAll()
    }

    override suspend fun getSubscriptionCount(key: K): Int = subscribers.value

    fun getAllKeys(): Flow<Set<K>> = flow {
        if (!fullyLoadedFromRepository.value) {
            loadFromStore()
            fullyLoadedFromRepository.value = true
        }
        emitAll(allKeys.values)
    }.onStart { subscribers.update { it + 1 } }
        .onCompletion { subscribers.update { it - 1 } }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic =
        ObservableCacheIndexStatistic(
            name = "Full",
            all = allKeys.size(),
            subscribed = subscribers.value.coerceAtMost(1),
        )
}

internal open class FullRepositoryObservableCache<K : Any, V>(
    repository: FullRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
    private val valueToKeyMapper: (V) -> K,
) : ObservableCache<K, V, FullRepositoryObservableCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = FullRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    clock = clock,
    expireDuration = expireDuration,
    removeFromCacheOnNull = true,
    values = values,
) {

    private val subscribersIndex = FullRepositoryObservableCacheIndex<K> {
        store.getAll().forEach { value ->
            val key = valueToKeyMapper(value)
            set(
                key = key,
                value = value,
                persistEnabled = false,
            )
        }
    }

    init {
        addIndex(subscribersIndex)
    }

    fun readAll(): Flow<Map<K, Flow<V?>>> =
        subscribersIndex.getAllKeys().map { keys ->
            keys.associateWith { get(it) }
        }
}