package net.folivo.trixnity.client.store.cache

import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

// TODO Currently the cache has the limitation, that some calls to readWithCache and writeWithCache could keep the cache full of values.
//  This happens if V is a collection and readWithCache and writeWithCache are used to retrieve less values, than the collection actually
//  holds at the moment. If that causes issues of huge caches, we should make the removerJobs a bit smarter.
open class StateFlowCache<K, V>(
    private val name: String,
    private val cacheScope: CoroutineScope,
    val expireDuration: Duration = 1.minutes,
) {
    private val infiniteCache = expireDuration.isInfinite()
    private val internalCache: MutableStateFlow<Map<K, StateFlowCacheValue<V?>>> = MutableStateFlow(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val cache = internalCache
        .mapLatest { value -> value.mapValues { it.value.value.asStateFlow() } }
        .shareIn(cacheScope, WhileSubscribed(replayExpirationMillis = 0))

    // TODO move this to a better place (like FullRepositoryStateFlowCache)
    fun init(initialValues: Map<K, V>) {
        require(infiniteCache) { "Cache cannot be initialized with values, when expireDuration is not infinite." }
        internalCache.value =
            initialValues.mapValues {
                val removeTime = MutableStateFlow(Duration.INFINITE)
                val stateFlowValue: MutableStateFlow<V?> = MutableStateFlow(it.value)
                val persistedFlows = MutableStateFlow(setOf<StateFlow<Boolean>>())
                StateFlowCacheValue(
                    stateFlowValue,
                    removeTime,
                    persistedFlows,
                    removeFromCacheJob(it.key, stateFlowValue.subscriptionCount, removeTime, persistedFlows)
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
            persist = { null })
        result.removerJob.start()
        emitAll(result.value)
    }

    suspend fun writeWithCache(
        key: K,
        updater: suspend (oldValue: V?) -> V?,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
        retrieveAndUpdateCache: suspend (cacheValue: V?) -> V?,
        persist: suspend (newValue: V?) -> StateFlow<Boolean>?
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
        persist: suspend (newValue: V?) -> StateFlow<Boolean>?
    ): StateFlowCacheValue<V?> =
        updateAndGet { oldCache ->
            val cacheValue = oldCache[key]
            if (cacheValue == null) {
                log.trace { "$name: no cache hit for key $key" }
                val retrievedValue = retrieveAndUpdateCache(null)
                val newValue = updater(retrievedValue)
                val persisted = persist(newValue)
                val persistedFlows = MutableStateFlow(setOfNotNull(persisted))
                val removeTime = MutableStateFlow(expireDuration)
                val newStateFlowValue: MutableStateFlow<V?> = MutableStateFlow(newValue)
                oldCache + (key to StateFlowCacheValue(
                    newStateFlowValue,
                    removeTime,
                    persistedFlows,
                    removeFromCacheJob(key, newStateFlowValue.subscriptionCount, removeTime, persistedFlows)
                ))
            } else {
                cacheValue.value.update { oldCacheValue ->
                    val oldValue =
                        if (isContainedInCache(oldCacheValue).not()) {
                            log.trace { "$name: no deep cache hit int $oldCacheValue for key $key" }
                            retrieveAndUpdateCache(oldCacheValue)
                        } else oldCacheValue
                    val newValue = updater(oldValue)
                    persist(newValue)?.let { persisted ->
                        cacheValue.persisted.update { it + persisted }
                    }
                    newValue
                }
                oldCache
            }
        }[key]
            .let { requireNotNull(it) { "We are sure, that it contains a value!" } }

    private fun removeFromCacheJob(
        key: K,
        subscriptionCountFlow: StateFlow<Int>,
        removeTimerFlow: StateFlow<Duration>,
        persistedFlows: StateFlow<Set<StateFlow<Boolean>>>,
    ): Job {
        return cacheScope.launch(start = CoroutineStart.LAZY) {
            if (infiniteCache.not())
                combine(
                    subscriptionCountFlow,
                    removeTimerFlow,
                    persistedFlows
                ) { subscriptionCount, removeTimer, persisted ->
                    Triple(subscriptionCount, removeTimer, persisted)
                }.collectLatest { (subscriptionCount, removeTimer, persisted) ->
                    delay(removeTimer)
                    // waiting for the cache value to be written into the repository
                    // otherwise cache and repository could get out of sync
                    persisted.forEach { persistedFlow ->
                        persistedFlow.first { it }
                    }
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

data class StateFlowCacheValue<T>(
    val value: MutableStateFlow<T>,
    val removeTimer: MutableStateFlow<Duration>,
    val persisted: MutableStateFlow<Set<StateFlow<Boolean>>>,
    val removerJob: Job
)