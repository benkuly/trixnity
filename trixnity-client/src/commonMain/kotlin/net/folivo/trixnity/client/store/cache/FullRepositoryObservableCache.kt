package net.folivo.trixnity.client.store.cache

import com.benasher44.uuid.uuid4
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
    private val subscribers = MutableStateFlow(setOf<String>())
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

    override suspend fun getSubscriptionCount(key: K): Flow<Int> = subscribers.map { it.size }

    fun getAllKeys(): Flow<Set<K>> = flow {
        if (!fullyLoadedFromRepository.value) {
            loadFromStore()
            fullyLoadedFromRepository.value = true
        }
        val subscriberId = uuid4().toString()
        emitAll(allKeys.values
            .onStart { subscribers.update { it + subscriberId } }
            .onCompletion { subscribers.update { it - subscriberId } }
        )
    }
}

internal class FullRepositoryObservableCache<K, V>(
    repository: FullRepository<K, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentMap<K, ObservableCacheValue<V?>> = ConcurrentMap(),
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