package net.folivo.trixnity.client.store.cache

import com.benasher44.uuid.uuid4
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

data class MapRepositoryCoroutinesCacheKey<K1, K2>(
    val firstKey: K1,
    val secondKey: K2,
)

private class MapRepositoryObservableIndex<K1, K2>(
    private val loadFromStore: suspend (key: K1) -> Unit,
) : ObservableMapIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> {
    private data class MapRepositoryObservableMapIndexValue<K2>(
        val keys: ConcurrentObservableSet<K2>,
        val fullyLoadedFromStore: Boolean = false,
        val subscribers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    )

    private val values = ConcurrentMap<K1, MapRepositoryObservableMapIndexValue<K2>>()

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        values.update(key.firstKey) { mapping ->
            mapping?.also { it.keys.add(key.secondKey) }
                ?: MapRepositoryObservableMapIndexValue(ConcurrentObservableSet(setOf(key.secondKey)))
        }
    }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>, stale: Boolean) {
        values.update(key.firstKey) { mapping ->
            if (mapping != null) {
                mapping.keys.remove(key.secondKey)
                when {
                    mapping.keys.size() == 0 -> null
                    mapping.fullyLoadedFromStore -> mapping.copy(fullyLoadedFromStore = stale)
                    else -> mapping
                }
            } else mapping
        }
    }

    override suspend fun onRemoveAll() {
        values.removeAll()
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): Flow<Int> =
        flow {
            val value = values.update(key.firstKey) {
                it ?: MapRepositoryObservableMapIndexValue(ConcurrentObservableSet())
            }
            checkNotNull(value)
            emitAll(value.subscribers.map { it.size })
        }

    fun getMapping(key: K1): Flow<Set<K2>> =
        flow {
            val fullyLoadedFromStore = values.get(key)?.fullyLoadedFromStore
            if (fullyLoadedFromStore != true) loadFromStore(key)
            val value = values.update(key) {
                it?.copy(fullyLoadedFromStore = true) ?: MapRepositoryObservableMapIndexValue(
                    ConcurrentObservableSet(),
                    fullyLoadedFromStore = true
                )
            }
            checkNotNull(value)
            val subscriberId = uuid4().toString()
            emitAll(
                value.keys.values
                    .onStart { value.subscribers.update { it + subscriberId } }
                    .onCompletion { value.subscribers.update { it - subscriberId } }
            )
        }
}

internal open class MapRepositoryObservableCache<K1, K2, V>(
    repository: MapRepository<K1, K2, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    values: ConcurrentMap<MapRepositoryCoroutinesCacheKey<K1, K2>, ObservableCacheValue<V?>> = ConcurrentMap(),
) : ObservableCache<MapRepositoryCoroutinesCacheKey<K1, K2>, V, MapRepositoryObservableCacheStore<K1, K2, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MapRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    values = values,
) {
    private val mapRepositoryIndex: MapRepositoryObservableIndex<K1, K2> =
        MapRepositoryObservableIndex { key ->
            log.trace { "load map from database by first key $key" }
            store.getByFirstKey(key).forEach { value ->
                updateAndGet(
                    key = MapRepositoryCoroutinesCacheKey(key, value.key),
                    get = { value.value },
                )
            }
        }

    init {
        addIndex(mapRepositoryIndex)
    }

    fun readByFirstKey(key: K1): Flow<Map<K2, Flow<V?>>> =
        mapRepositoryIndex.getMapping(key).map { mapping ->
            mapping.associateWith { secondKey ->
                read(MapRepositoryCoroutinesCacheKey(key, secondKey))
            }
        }
}