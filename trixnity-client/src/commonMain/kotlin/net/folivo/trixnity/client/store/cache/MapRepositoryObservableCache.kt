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

private class MapRepositoryObservableMapIndex<K1, K2>(
    private val cacheScope: CoroutineScope,
    private val get: suspend (key: K1) -> Set<K2>,
) : ObservableMapIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> {
    private data class MapRepositoryObservableMapIndexValue<K2>(
        val keys: ObservableSet<K2>,
        val fullyLoadedFromRepository: Boolean = false,
        val subscribers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    )
    
    private val values = ObservableMap<K1, MapRepositoryObservableMapIndexValue<K2>>(cacheScope)

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        val mapping = values.get(key.firstKey)
        if (mapping != null && mapping.fullyLoadedFromRepository) {
            mapping.keys.add(key.secondKey)
        }
    }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        values.update(key.firstKey) { mapping ->
            if (mapping != null) {
                when {
                    mapping.keys.size() == 1 -> null
                    mapping.fullyLoadedFromRepository -> mapping.copy(fullyLoadedFromRepository = false)
                    else -> mapping
                }
            } else mapping
        }?.keys?.remove(key.secondKey)
    }

    override suspend fun onRemoveAll() {
        values.removeAll()
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): Flow<Int> =
        flow {
            val value = values.update(key.firstKey) {
                it ?: MapRepositoryObservableMapIndexValue(ObservableSet(cacheScope))
            }
            checkNotNull(value)
            emitAll(value.subscribers.map { it.size })
        }

    fun getMapping(key: K1): Flow<Set<K2>?> =
        flow {
            val value = values.update(key) { mapping ->
                when {
                    mapping == null -> MapRepositoryObservableMapIndexValue(ObservableSet(cacheScope, get(key)), true)
                    mapping.fullyLoadedFromRepository.not() -> mapping.copy(
                        keys = mapping.keys.also { it.addAll(get(key)) },
                        fullyLoadedFromRepository = true
                    )

                    else -> mapping
                }
            }
            checkNotNull(value)
            val subscriberId = uuid4().toString()
            emitAll(
                value.keys.values
                    .onSubscription { value.subscribers.update { it + subscriberId } }
                    .onCompletion { value.subscribers.update { it - subscriberId } }
            )
        }
}

internal open class MapRepositoryObservableCache<K1, K2, V>(
    repository: MapRepository<K1, K2, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    values: ObservableMap<MapRepositoryCoroutinesCacheKey<K1, K2>, ObservableCacheValue<V?>> = ObservableMap(cacheScope),
) : ObservableCache<MapRepositoryCoroutinesCacheKey<K1, K2>, V, MapRepositoryObservableCacheStore<K1, K2, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MapRepositoryObservableCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    values = values,
) {
    private val mapRepositoryIndex: MapRepositoryObservableMapIndex<K1, K2> =
        MapRepositoryObservableMapIndex(cacheScope) { key ->
            log.trace { "load map from database by first key $key" }
            val getByFirstKey = store.getByFirstKey(key)
            getByFirstKey.forEach { value ->
                updateAndGet(
                    key = MapRepositoryCoroutinesCacheKey(key, value.key),
                    get = { value.value },
                )
            }
            getByFirstKey.keys
        }

    init {
        addIndex(mapRepositoryIndex)
    }

    fun readByFirstKey(key: K1): Flow<Map<K2, Flow<V?>>?> =
        mapRepositoryIndex.getMapping(key).map { mapping ->
            mapping?.associateWith { secondKey ->
                read(MapRepositoryCoroutinesCacheKey(key, secondKey))
            }
        }
}