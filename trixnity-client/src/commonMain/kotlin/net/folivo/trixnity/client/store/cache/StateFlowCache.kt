package net.folivo.trixnity.client.store.cache

import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

/* TODO Currently the cache has the limitation, that some calls to readWithCache and writeWithCache could keep the cache full of values.
* This happens if V is a collection and readWithCache and writeWithCache are used to retrieve less values, than the collection actually
* holds at the moment. If that causes issues of huge caches, we should make the removerJobs a bit smarter. */
open class StateFlowCache<K, V>(
    private val name: String,
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
                val stateFlowValue: MutableStateFlow<V?> = MutableStateFlow(it.value)
                StateFlowCacheValue(
                    stateFlowValue,
                    removeTime,
                    removeFromCacheJob(it.key, stateFlowValue.subscriptionCount, removeTime)
                )
            }.toMap()
    }

    fun reset() {
        internalCache.value = emptyMap()
    }

    fun readWithCache(
        key: K,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
    ): Flow<V?> = flow {
        val result = internalCache.update(
            key = key,
            updater = { it },
            isContainedInCache = isContainedInCache,
            retrieveAndUpdateCache = retrieveAndUpdateCache,
            persist = {})
        result.removerJob.start()
        emitAll(result.value)
    }

    suspend fun writeWithCache(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        persist: suspend (newValue: V?) -> Unit
    ) {
        val result = internalCache.update(
            key = key,
            updater = updater,
            isContainedInCache = isContainedInCache,
            retrieveAndUpdateCache = retrieveAndUpdateCache,
            persist = persist
        )
        result.removerJob.start()
    }

    private suspend fun MutableStateFlow<Map<K, StateFlowCacheValue<V?>>>.update(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        persist: suspend (newValue: V?) -> Unit
    ): StateFlowCacheValue<V?> =
        updateAndGet { oldCache ->
            val cacheValue = oldCache[key]
            if (cacheValue == null) {
                log.trace { "$name: no cache hit for key $key" }
                val retrievedValue = retrieveAndUpdateCache(null)
                val newValue = updater(retrievedValue)
                    .also { persist(it) }
                val removeTime = MutableStateFlow(cacheDuration)
                val newStateFlowValue: MutableStateFlow<V?> = MutableStateFlow(newValue)
                oldCache + (key to StateFlowCacheValue(
                    newStateFlowValue,
                    removeTime,
                    removeFromCacheJob(key, newStateFlowValue.subscriptionCount, removeTime)
                ))
            } else {
                cacheValue.value.update { oldCacheValue ->
                    val oldValue =
                        if (isContainedInCache(oldCacheValue).not()) {
                            log.trace { "$name: no deep cache hit int $oldCacheValue for key $key" }
                            retrieveAndUpdateCache(oldCacheValue)
                        } else oldCacheValue
                    updater(oldValue)
                        .also { persist(it) }
                }
                oldCache
            }
        }[key]
            .let { requireNotNull(it) { "We are sure, that it contains a value!" } }

    private fun removeFromCacheJob(
        key: K,
        subscriptionCountFlow: StateFlow<Int>,
        removeTimerFlow: StateFlow<Duration>
    ): Job {
        return cacheScope.launch(start = CoroutineStart.LAZY) {
            if (infiniteCache.not())
                combine(subscriptionCountFlow, removeTimerFlow) { subscriptionCount, removeTimer ->
                    subscriptionCount to removeTimer
                }.collectLatest { (subscriptionCount, removeTimer) ->
                    delay(removeTimer)
                    internalCache.update {
                        if (subscriptionCount == 0) {
                            log.trace { "$name: remove value from cache with key $key" }
                            it - key
                        } else it
                    }
                }
        }
    }
}