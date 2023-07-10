package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class MapRepositoryCoroutinesCacheKey<K1, K2>(
    val firstKey: K1,
    val secondKey: K2,
)

private data class MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>(
    val keys: Set<MapRepositoryCoroutinesCacheKey<K1, K2>>,
    val fullyLoadedFromRepository: Boolean,
)

private class MapRepositoryObservableMapIndex<K1, K2>(
    private val cacheScope: CoroutineScope,
) : ObservableMapIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> {

    private val firstKeyMapping =
        ObservableMap<K1, MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>>>(cacheScope)

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        firstKeyMapping.update(key.firstKey) { mapping ->
            if (mapping == null) {
                MutableStateFlow(
                    MapRepositoryCoroutineCacheValuesIndexValue(setOf(key), false)
                ).launchRemoveOnEmptySubscriptionCount(key.firstKey)

            } else {
                mapping.update { it.copy(keys = it.keys + key) }
                mapping
            }
        }
    }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        firstKeyMapping.get(key.firstKey)?.update {
            it.copy(
                keys = it.keys - key,
                fullyLoadedFromRepository = false
            )
        }
    }

    override suspend fun onRemoveAll() {
        firstKeyMapping.getAll().forEach { entry ->
            entry.value.update {
                it.copy(
                    keys = emptySet(),
                    fullyLoadedFromRepository = false
                )
            }
        }
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): StateFlow<Int> =
        checkNotNull(firstKeyMapping.get(key.firstKey)?.subscriptionCount)

    private fun MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>>.launchRemoveOnEmptySubscriptionCount(
        key: K1
    ): MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>> =
        also {
            cacheScope.launch {
                delay(1.seconds) // prevent, that empty values are removed immediately
                combine(this@launchRemoveOnEmptySubscriptionCount, subscriptionCount) { mapping, subscriptionCount ->
                    if (mapping.keys.isEmpty() && subscriptionCount == 0) {
                        firstKeyMapping.update(key) { null }
                    }
                }.collect()
            }
        }

    fun getMapping(key: K1): Flow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>> = flow {
        emitAll(
            checkNotNull(
                firstKeyMapping.update(key) { mapping ->
                    mapping
                        ?: MutableStateFlow(
                            MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>(setOf(), false)
                        ).launchRemoveOnEmptySubscriptionCount(key)
                }
            )
        )
    }

    suspend fun markFullyLoadedFromRepository(key: K1) {
        firstKeyMapping.get(key)?.update { it.copy(fullyLoadedFromRepository = true) }
    }
}

open class MapRepositoryCoroutineCache<K1, K2, V>(
    repository: MapRepository<K1, K2, V>,
    tm: TransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
) : CoroutineCache<MapRepositoryCoroutinesCacheKey<K1, K2>, V, MapRepositoryCoroutineCacheStore<K1, K2, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = MapRepositoryCoroutineCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration
) {
    private val mapRepositoryIndex: MapRepositoryObservableMapIndex<K1, K2> =
        MapRepositoryObservableMapIndex(cacheScope)

    init {
        addIndex(mapRepositoryIndex)
    }

    fun readByFirstKey(key: K1): Flow<Map<K2, Flow<V?>>?> = flow {
        emitAll(
            mapRepositoryIndex.getMapping(key)
                .distinctUntilChanged()
                .transform { firstKeyMapping ->
                    if (firstKeyMapping.fullyLoadedFromRepository) {
                        emit(
                            firstKeyMapping.keys.associate { key ->
                                key.secondKey to
                                        updateAndGet(
                                            key = key,
                                            updater = null,
                                            get = { store.get(key) },
                                            persist = { null },
                                        )
                            }
                        )
                    } else {
                        store.getByFirstKey(key).forEach { value ->
                            updateAndGet(
                                key = MapRepositoryCoroutinesCacheKey(key, value.key),
                                updater = null,
                                get = { value.value },
                                persist = { null },
                            )
                        }
                        mapRepositoryIndex.markFullyLoadedFromRepository(key)
                    }
                }
        )
    }
}