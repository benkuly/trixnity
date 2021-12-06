package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/* TODO Currently the cache has the limitation, that some calls to readWithCache and writeWithCache could keep the cache full of values.
* This happens if V is a collection and readWithCache and writeWithCache are used to retrieve less values, than the collection actually
* holds at the moment. If that causes issues of huge caches, we should make the removerJobs a bit smarter. */
@OptIn(ExperimentalTime::class)
class StateFlowCache<K, V, R : MinimalStoreRepository<K, V>>(
    private val cacheScope: CoroutineScope,
    private val repository: R,
    private val infiniteCache: Boolean = false,
    private val readCacheTime: Duration = Duration.seconds(5),
    private val writeCacheTime: Duration = Duration.seconds(5)
) {
    private val _cache: MutableStateFlow<Map<K, StateFlowCacheValue<V?>>> = MutableStateFlow(emptyMap())
    val cache = _cache
        .map { value -> value.mapValues { it.value.value.asStateFlow() } }
        .stateIn(cacheScope, WhileSubscribed(replayExpirationMillis = 0), mapOf())

    fun init(initialValues: Map<K, V>) {
        require(infiniteCache) { "Cache cannot be initialized with values, when infiniteCache is disabled." }
        _cache.value =
            initialValues.mapValues { StateFlowCacheValue<V?>(MutableStateFlow(it.value), setOf(), null) }.toMap()
    }

    fun reset() {
        _cache.value = emptyMap()
    }

    suspend fun readWithCache(
        key: K,
        containsInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveFromRepoAndUpdateCache: suspend (cacheValue: V?, repository: R) -> V?,
        scope: CoroutineScope? = null
    ): StateFlow<V?> {
        val result = _cache.updateAndGet { oldCache ->
            val cacheValue = oldCache[key]

            val job = scope?.coroutineContext?.get(Job)
            val newSubscribers = if (job == null || infiniteCache) setOf() else setOf(scope)

            // We try to remove the value from cache, when there is a job, we can listen to.
            if (infiniteCache.not())
                job?.invokeOnCompletion {
                    cacheScope.launch {
                        _cache.update {
                            val currentValue = it[key]
                            when {
                                currentValue == null -> it
                                currentValue.subscribers.size <= 1 && currentValue.removerJob == null -> it - key
                                else -> it + (key to currentValue.copy(subscribers = currentValue.subscribers - scope))
                            }
                        }
                    }
                }
            // If there is no job, we create one with a hard delay. If someone reads the cache value, the old job gets
            // cancelled and a new one will be created.
            val removerJob = if (job == null && infiniteCache.not()) removeFromCacheJob(key, readCacheTime) else null

            if (cacheValue == null) {
                val databaseValue = MutableStateFlow(retrieveFromRepoAndUpdateCache(null, repository))
                oldCache + (key to StateFlowCacheValue(databaseValue, newSubscribers, removerJob))
            } else {
                cacheValue.removerJob?.cancelAndJoin()
                cacheValue.value.update {
                    if (containsInCache(it).not()) retrieveFromRepoAndUpdateCache(it, repository)
                    else it
                }
                oldCache + (key to cacheValue.copy(
                    subscribers = cacheValue.subscribers + newSubscribers,
                    removerJob = removerJob
                ))
            }
        }[key]
        requireNotNull(result) { "We are sure, that it contains a value!" }
        result.removerJob?.start()
        return result.value.asStateFlow()
    }

    suspend fun get(key: K): V? {
        return readWithCache(
            key,
            containsInCache = { it != null },
            retrieveFromRepoAndUpdateCache = { _, repository -> repository.get(key) },
            null
        ).value
    }

    suspend fun get(key: K, scope: CoroutineScope): StateFlow<V?> {
        return readWithCache(
            key,
            containsInCache = { it != null },
            retrieveFromRepoAndUpdateCache = { _, repository -> repository.get(key) },
            scope
        )
    }

    suspend fun getWithInfiniteMode(key: K): StateFlow<V?> {
        return readWithCache(
            key,
            containsInCache = { infiniteCache || it != null },
            retrieveFromRepoAndUpdateCache = { cacheValue, repo ->
                if (infiniteCache) cacheValue
                else repo.get(key)
            },
            null
        )
    }

    suspend fun writeWithCache(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        containsInCache: suspend (cacheValue: V?) -> Boolean,
        getFromRepositoryAndUpdateCache: suspend (cacheValue: V?, repository: R) -> V?,
        persistIntoRepository: suspend (newValue: V?, repository: R) -> Unit
    ) {
        val result = _cache.updateAndGet { oldCache ->
            val cacheValue = oldCache[key]
            val removerJob = if (infiniteCache.not()) removeFromCacheJob(key, writeCacheTime) else null
            val newCacheValue: StateFlowCacheValue<V?>? = if (cacheValue == null) {
                val valueFromDb = getFromRepositoryAndUpdateCache(null, repository)
                val newValue = updater(valueFromDb)
                    .also { persistIntoRepository(it, repository) }
                newValue?.let { StateFlowCacheValue(MutableStateFlow(newValue), setOf(), removerJob) }
            } else {
                val newValue = cacheValue.value.updateAndGet { oldCacheValue ->
                    val oldValue =
                        if (containsInCache(oldCacheValue).not())
                            getFromRepositoryAndUpdateCache(oldCacheValue, repository)
                        else oldCacheValue
                    updater(oldValue)
                        .also { persistIntoRepository(it, repository) }
                }
                cacheValue.removerJob?.cancelAndJoin()
                newValue?.let { cacheValue.copy(removerJob = removerJob) }
            }
            if (newCacheValue == null) oldCache - key
            else oldCache + (key to newCacheValue)
        }[key]
        result?.removerJob?.start()
    }

    suspend fun update(key: K, updater: suspend (oldValue: V?) -> V?) {
        writeWithCache(key, updater,
            containsInCache = { infiniteCache || it != null },
            getFromRepositoryAndUpdateCache = { cacheValue, repo ->
                if (infiniteCache) cacheValue
                else repo.get(key)
            },
            persistIntoRepository = { newValue, repo ->
                if (newValue == null) repo.delete(key)
                else repo.save(key, newValue)
            })
    }

    private fun removeFromCacheJob(key: K, delay: Duration): Job {
        return cacheScope.launch(start = LAZY) {
            delay(delay)
            _cache.update {
                val currentValue = it[key]
                if (currentValue?.subscribers?.size == 0) it - key
                else it
            }
        }
    }
}