package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

data class MapRepositoryCoroutinesCacheKey<K1, K2>(
    val firstKey: K1,
    val secondKey: K2,
)

private class MapRepositoryObservableCacheIndex<K1, K2>(
    private val name: String,
    private val loadFromStore: suspend (key: K1) -> Unit,
) : ObservableCacheIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> {
    private data class MapRepositoryObservableMapIndexValue<K2>(
        val keys: ConcurrentObservableSet<K2> = ConcurrentObservableSet(),
        val fullyLoadedFromStore: Boolean = false,
        val subscribers: MutableStateFlow<Int> = MutableStateFlow(0),
    )

    private val values = ConcurrentObservableMap<K1, MapRepositoryObservableMapIndexValue<K2>>()

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        log.trace { "$name: put key $key into map index" }
        values.getOrPut(key.firstKey) { MapRepositoryObservableMapIndexValue() }
            .keys.add(key.secondKey)
    }

    override suspend fun onSkipPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        values.update(key.firstKey) { mapping ->
            if (mapping != null && mapping.fullyLoadedFromStore) {
                log.trace { "$name: mark map index as not fully loaded from store for key $key" }
                mapping.copy(fullyLoadedFromStore = false)
            } else mapping
        }
    }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>, stale: Boolean) {
        values.update(key.firstKey) { mapping ->
            if (mapping != null) {
                log.trace { "$name: remove key $key from map index" }
                mapping.keys.remove(key.secondKey)
                when {
                    mapping.keys.size() == 0 -> null
                    mapping.fullyLoadedFromStore -> mapping.copy(fullyLoadedFromStore = mapping.fullyLoadedFromStore && stale)
                    else -> mapping
                }
            } else mapping
        }
    }

    override suspend fun onRemoveAll() {
        values.removeAll()
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): Int =
        values.getOrPut(key.firstKey) { MapRepositoryObservableMapIndexValue() }.subscribers.value


    fun getMapping(key: K1): Flow<Set<K2>> =
        flow {
            val fullyLoadedFromStore = values.get(key)?.fullyLoadedFromStore
            if (fullyLoadedFromStore != true) {
                log.trace { "$name: not fully loaded from store. load now for key $key" }
                loadFromStore(key)
            }
            val value = values.update(key) {
                it?.copy(fullyLoadedFromStore = true)
                    ?: MapRepositoryObservableMapIndexValue(fullyLoadedFromStore = true)
            }
            checkNotNull(value)
            emitAll(
                value.keys.values
                    .onStart { value.subscribers.update { it + 1 } }
                    .onCompletion { value.subscribers.update { it - 1 } }
            )
        }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic {
        val (all, subscribed) = values.internalRead {
            count() to values.count { it.subscribers.value > 0 }
        }
        return ObservableCacheIndexStatistic(
            name = "Map",
            all = all,
            subscribed = subscribed,
        )
    }
}

internal open class MapRepositoryObservableCache<K1, K2, V>(
    repository: MapRepository<K1, K2, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentObservableMap<MapRepositoryCoroutinesCacheKey<K1, K2>, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
) : ObservableCache<MapRepositoryCoroutinesCacheKey<K1, K2>, V, MapRepositoryObservableCacheStore<K1, K2, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MapRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    clock = clock,
    values = values,
) {
    private val mapRepositoryIndex: MapRepositoryObservableCacheIndex<K1, K2> =
        MapRepositoryObservableCacheIndex(name) { key ->
            log.trace { "load map from database by first key $key" }
            store.getByFirstKey(key).forEach { value ->
                set(
                    key = MapRepositoryCoroutinesCacheKey(key, value.key),
                    value = value.value,
                    persistEnabled = false,
                )
            }
        }

    init {
        addIndex(mapRepositoryIndex)
    }

    fun readByFirstKey(key: K1): Flow<Map<K2, Flow<V?>>> =
        mapRepositoryIndex.getMapping(key).map { mapping ->
            mapping.associateWith { secondKey ->
                get(MapRepositoryCoroutinesCacheKey(key, secondKey))
            }
        }
}