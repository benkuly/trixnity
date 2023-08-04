package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

data class MapRepositoryCoroutinesCacheKey<K1, K2>(
    val firstKey: K1,
    val secondKey: K2,
)

private data class MapRepositoryObservableMapIndexValue<K2>(
    val keys: Set<K2> = setOf(),
    val fullyLoadedFromRepository: Boolean = false,
)

private class MapRepositoryObservableMapIndex<K1, K2>(
    name: String,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    private val get: suspend (key: K1) -> Set<K2>,
) : ObservableMapIndex<MapRepositoryCoroutinesCacheKey<K1, K2>>,
    CoroutineCacheBase<K1, MapRepositoryObservableMapIndexValue<K2>>(
        name = name,
        cacheScope = cacheScope,
        expireDuration = expireDuration
    ) {

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        // we only put, when mapping is fullyLoadedFromRepository
        updateAndGet(
            key = key.firstKey,
            updater = { mapping ->
                if (mapping != null && mapping.fullyLoadedFromRepository) {
                    mapping.copy(keys = mapping.keys + key.secondKey)
                } else mapping
            },
            get = { null },
        )
    }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        // don't remove any mapping
    }

    override suspend fun onRemoveAll() {
        clear()
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): Flow<Int> =
        flow {
            val result = updateAndGet(
                key = key.firstKey,
                updater = { it ?: MapRepositoryObservableMapIndexValue() },
                get = { null },
            )
            emitAll(result.value.subscriptionCount)
        }

    fun getMapping(key: K1): Flow<Set<K2>?> =
        flow {
            emitAll(
                updateAndGet(
                    key = key,
                    updater = { mapping ->
                        if (mapping == null || mapping.fullyLoadedFromRepository.not())
                            MapRepositoryObservableMapIndexValue(get(key), true)
                        else mapping
                    },
                    get = { null },
                ).value.map { it?.keys }
            )
        }
}

internal open class MapRepositoryCoroutineCache<K1, K2, V>(
    repository: MapRepository<K1, K2, V>,
    tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
) : CoroutineCache<MapRepositoryCoroutinesCacheKey<K1, K2>, V, MapRepositoryCoroutineCacheStore<K1, K2, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MapRepositoryCoroutineCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration
) {
    private val mapRepositoryIndex: MapRepositoryObservableMapIndex<K1, K2> =
        MapRepositoryObservableMapIndex(
            name = (repository::class.simpleName ?: repository::class.toString()) + "MapIndex",
            cacheScope = cacheScope,
            expireDuration = expireDuration,
        ) { key ->
            log.trace { "load map by first key $key" }
            val getByFirstKey = store.getByFirstKey(key)
            getByFirstKey.forEach { value ->
                updateAndGet(
                    key = MapRepositoryCoroutinesCacheKey(key, value.key),
                    updater = null,
                    get = { value.value },
                    persist = { },
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