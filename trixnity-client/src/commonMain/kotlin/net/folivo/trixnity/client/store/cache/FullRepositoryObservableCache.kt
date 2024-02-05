package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.repository.FullRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private class FullRepositoryObservableCacheIndex<K>(
    private val loadFromStore: suspend () -> Unit,
) : ObservableMapIndex<K> {

    private val allKeys = ConcurrentObservableSet<K>()
    private val subscribers = MutableStateFlow(0)
    private val fullyLoadedFromRepository = MutableStateFlow(false)
    override suspend fun onPut(key: K) {
        allKeys.add(key)
    }

    override suspend fun onRemove(key: K, stale: Boolean) {
        fullyLoadedFromRepository.value = stale
        allKeys.remove(key)
    }

    override suspend fun onRemoveAll() {
        allKeys.removeAll()
    }

    override suspend fun getSubscriptionCount(key: K): Flow<Int> = subscribers

    fun getAllKeys(): Flow<Set<K>> = flow {
        if (!fullyLoadedFromRepository.value) {
            loadFromStore()
            fullyLoadedFromRepository.value = true
        }
        emitAll(allKeys.values
            .onStart { subscribers.update { it + 1 } }
            .onCompletion { subscribers.update { it - 1 } }
        )
    }
}

internal class FullRepositoryObservableCache<K, V>(
    repository: FullRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
    private val valueToKeyMapper: (V) -> K,
) : ObservableCache<K, V, FullRepositoryObservableCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = FullRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    removeFromCacheOnNull = true,
    values = values,
) {

    private val subscribersIndex = FullRepositoryObservableCacheIndex<K> {
        store.getAll().forEach { value ->
            val key = valueToKeyMapper(value)
            updateAndGet(
                key = key,
                get = { value },
            )
        }
    }

    init {
        addIndex(subscribersIndex)
    }

    fun readAll(): Flow<Map<K, Flow<V?>>> =
        subscribersIndex.getAllKeys().map { keys ->
            keys.associateWith { read(it) }
        }
}