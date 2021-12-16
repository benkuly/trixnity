package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/* TODO Currently the cache has the limitation, that some calls to readWithCache and writeWithCache could keep the cache full of values.
* This happens if V is a collection and readWithCache and writeWithCache are used to retrieve less values, than the collection actually
* holds at the moment. If that causes issues of huge caches, we should make the removerJobs a bit smarter. */
@OptIn(ExperimentalTime::class)
open class StateFlowCache<K, V>(
    private val cacheScope: CoroutineScope,
    val infiniteCache: Boolean = false,
    private val readCacheTime: Duration = Duration.minutes(1),
    private val writeCacheTime: Duration = Duration.minutes(1)
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
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
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
                val databaseValue = MutableStateFlow(retrieveAndUpdateCache(null))
                oldCache + (key to StateFlowCacheValue(databaseValue, newSubscribers, removerJob))
            } else {
                cacheValue.removerJob?.cancelAndJoin()
                cacheValue.value.update {
                    if (containsInCache(it).not()) retrieveAndUpdateCache(it)
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

    suspend fun writeWithCache(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        containsInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        persist: suspend (newValue: V?) -> Unit
    ) {
        val result = _cache.updateAndGet { oldCache ->
            val cacheValue = oldCache[key]
            val removerJob = if (infiniteCache.not()) removeFromCacheJob(key, writeCacheTime) else null
            val newCacheValue: StateFlowCacheValue<V?>? = if (cacheValue == null) {
                val valueFromDb = retrieveAndUpdateCache(null)
                val newValue = updater(valueFromDb)
                    .also { persist(it) }
                newValue?.let { StateFlowCacheValue(MutableStateFlow(newValue), setOf(), removerJob) }
            } else {
                val newValue = cacheValue.value.updateAndGet { oldCacheValue ->
                    val oldValue =
                        if (containsInCache(oldCacheValue).not())
                            retrieveAndUpdateCache(oldCacheValue)
                        else oldCacheValue
                    updater(oldValue)
                        .also { persist(it) }
                }
                cacheValue.removerJob?.cancelAndJoin()
                newValue?.let { cacheValue.copy(removerJob = removerJob) }
            }
            if (newCacheValue == null) oldCache - key
            else oldCache + (key to newCacheValue)
        }[key]
        result?.removerJob?.start()
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