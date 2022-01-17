package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/* TODO Currently the cache has the limitation, that some calls to readWithCache and writeWithCache could keep the cache full of values.
* This happens if V is a collection and readWithCache and writeWithCache are used to retrieve less values, than the collection actually
* holds at the moment. If that causes issues of huge caches, we should make the removerJobs a bit smarter. */
open class StateFlowCache<K, V>(
    private val cacheScope: CoroutineScope,
    val infiniteCache: Boolean = false,
    val cacheDuration: Duration = 1.minutes,
) {
    private val internalCache: MutableStateFlow<Map<K, StateFlowCacheValue<V?>>> = MutableStateFlow(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cache = internalCache
        .mapLatest { value -> value.mapValues { it.value.value.asStateFlow() } }
        .shareIn(cacheScope, WhileSubscribed(replayExpirationMillis = 0))

    fun init(initialValues: Map<K, V>) {
        require(infiniteCache) { "Cache cannot be initialized with values, when infiniteCache is disabled." }
        internalCache.value =
            initialValues.mapValues {
                val removeTime = MutableStateFlow(Duration.INFINITE)
                StateFlowCacheValue<V?>(
                    MutableStateFlow(it.value),
                    setOf(),
                    removeTime,
                    removeFromCacheJob(it.key, removeTime)
                )
            }.toMap()
    }

    fun reset() {
        internalCache.value = emptyMap()
    }

    suspend fun readWithCache(
        key: K,
        containsInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        scope: CoroutineScope? = null
    ): StateFlow<V?> {
        val result = internalCache.updateAndGet { oldCache ->
            val cacheValue = oldCache[key]

            val job = scope?.coroutineContext?.get(Job)
            val newSubscribers = if (job == null || infiniteCache) setOf() else setOf(scope)

            // We try to remove the value from cache, when there is a job, we can listen to.
            if (infiniteCache.not())
                job?.invokeOnCompletion {
                    cacheScope.launch {
                        internalCache.update {
                            when (val currentValue = it[key]) {
                                null -> it
                                else -> {
                                    currentValue.removeTimer.emit(cacheDuration)
                                    it + (key to currentValue.copy(subscribers = currentValue.subscribers - scope))
                                }
                            }
                        }
                    }
                }

            if (cacheValue == null) {
                val databaseValue = MutableStateFlow(retrieveAndUpdateCache(null))
                val removeTime = MutableStateFlow(cacheDuration)
                oldCache + (key to StateFlowCacheValue(
                    databaseValue,
                    newSubscribers,
                    removeTime,
                    removeFromCacheJob(key, removeTime)
                ))
            } else {
                cacheValue.removeTimer.emit(cacheDuration)
                cacheValue.value.update {
                    if (containsInCache(it).not()) retrieveAndUpdateCache(it)
                    else it
                }
                oldCache + (key to cacheValue.copy(
                    subscribers = cacheValue.subscribers + newSubscribers,
                ))
            }
        }[key]
        requireNotNull(result) { "We are sure, that it contains a value!" }
        result.removerJob.start()
        return result.value.asStateFlow()
    }

    suspend fun writeWithCache(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        containsInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        persist: suspend (newValue: V?) -> Unit
    ) {
        val result = internalCache.updateAndGet { oldCache ->
            val cacheValue = oldCache[key]
            val newCacheValue: StateFlowCacheValue<V?>? =
                if (cacheValue == null) {
                    val valueFromDb = retrieveAndUpdateCache(null)
                    val newValue = updater(valueFromDb)
                        .also { persist(it) }
                    val removeTime = MutableStateFlow(cacheDuration)
                    newValue?.let {
                        StateFlowCacheValue(
                            MutableStateFlow(newValue),
                            setOf(),
                            removeTime,
                            removeFromCacheJob(key, removeTime)
                        )
                    }
                } else {
                    val newValue = cacheValue.value.updateAndGet { oldCacheValue ->
                        val oldValue =
                            if (containsInCache(oldCacheValue).not())
                                retrieveAndUpdateCache(oldCacheValue)
                            else oldCacheValue
                        updater(oldValue)
                            .also { persist(it) }
                    }
                    newValue?.let { cacheValue }
                }
            if (newCacheValue == null) oldCache - key
            else oldCache + (key to newCacheValue)
        }[key]
        result?.removerJob?.start()
    }

    private fun removeFromCacheJob(key: K, removeTimer: StateFlow<Duration>): Job {
        return cacheScope.launch(start = CoroutineStart.LAZY) {
            if (infiniteCache.not())
                removeTimer.collectLatest { duration ->
                    delay(duration)
                    internalCache.update {
                        if (it[key]?.subscribers?.size == 0) it - key
                        else it
                    }
                }
        }
    }
}