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

private class MapRepositoryCoroutineCacheValuesIndex<K1, K2>(
    private val cacheScope: CoroutineScope,
) : CoroutineCacheValuesIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> {

    private val firstKeyMapping =
        MutableStateFlow<Map<K1, MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>>>>(emptyMap())

    override suspend fun onPut(key: MapRepositoryCoroutinesCacheKey<K1, K2>): Unit =
        firstKeyMapping.update { mappings ->
            val mapping = mappings[key.firstKey]
            if (mapping == null) {
                mappings + (
                        key.firstKey to MutableStateFlow(
                            MapRepositoryCoroutineCacheValuesIndexValue(setOf(key), false)
                        ).launchRemoveOnEmptySubscriptionCount(key.firstKey)
                        )
            } else {
                mapping.update { it.copy(keys = it.keys + key) }
                mappings
            }
        }

    override suspend fun onRemove(key: MapRepositoryCoroutinesCacheKey<K1, K2>) {
        firstKeyMapping.value[key.firstKey]?.update {
            it.copy(
                keys = it.keys - key,
                fullyLoadedFromRepository = false
            )
        }
    }

    override suspend fun getSubscriptionCount(key: MapRepositoryCoroutinesCacheKey<K1, K2>): StateFlow<Int> =
        checkNotNull(firstKeyMapping.value[key.firstKey]?.subscriptionCount)

    private fun MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>>.launchRemoveOnEmptySubscriptionCount(
        key: K1
    ): MutableStateFlow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>> =
        also {
            cacheScope.launch {
                delay(1.seconds) // prevent, that empty values are removed immediately
                combine(this@launchRemoveOnEmptySubscriptionCount, subscriptionCount) { mapping, subscriptionCount ->
                    if (mapping.keys.isEmpty() && subscriptionCount == 0) {
                        firstKeyMapping.update { it - key }
                    }
                }.collect()
            }
        }

    fun getMapping(key: K1): Flow<MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>> = flow {
        emitAll(
            checkNotNull(
                firstKeyMapping.updateAndGet { mappings ->
                    val mapping = mappings[key]
                    if (mapping == null) {
                        mappings + (
                                key to
                                        MutableStateFlow(
                                            MapRepositoryCoroutineCacheValuesIndexValue<K1, K2>(setOf(), false)
                                        ).launchRemoveOnEmptySubscriptionCount(key)
                                )
                    } else {
                        mappings
                    }
                }[key]
            )
        )
    }

    fun markFullyLoadedFromRepository(key: K1) {
        firstKeyMapping.value[key]?.update { it.copy(fullyLoadedFromRepository = true) }
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
    private val mapRepositoryIndex: MapRepositoryCoroutineCacheValuesIndex<K1, K2> =
        MapRepositoryCoroutineCacheValuesIndex(cacheScope)

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
                        store.getByFirstKey(key).orEmpty().forEach { value ->
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