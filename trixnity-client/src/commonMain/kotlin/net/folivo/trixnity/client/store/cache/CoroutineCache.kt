package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

private data class CoroutineCacheValue<T>(
    val value: MutableStateFlow<T>,
    val removeTimer: MutableStateFlow<Duration>,
    val persisted: MutableStateFlow<Set<StateFlow<Boolean>>>,
    val removerJob: Lazy<Job>
)

open class CoroutineCache<K, V, S : CoroutineCacheStore<K, V>>(
    protected val name: String,
    protected val store: S,
    protected val cacheScope: CoroutineScope,
    protected val expireDuration: Duration = 1.minutes,
) {
    private val _values = MutableStateFlow<Map<K, CoroutineCacheValue<V?>>>(emptyMap())
    val values: SharedFlow<Map<K, StateFlow<V?>>> =
        _values.map { value -> value.mapValues { it.value.value.asStateFlow() } }
            .shareIn(cacheScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0))

    private val indexes = MutableStateFlow(listOf<CoroutineCacheValuesIndex<K>>())

    fun addIndex(index: CoroutineCacheValuesIndex<K>) {
        indexes.update { it + index }
    }

    fun clear() {
        _values.updateAndGet { emptyMap() }
    }

    suspend fun deleteAll() {
        store.deleteAll()
        _values.updateAndGet { emptyMap() }
    }

    fun read(key: K): Flow<V?> = flow {
        emitAll(
            updateAndGet(
                key = key,
                updater = null,
                get = { store.get(key) },
                persist = { null },
            )
        )
    }

    suspend fun write(
        key: K,
        persistEnabled: Boolean = true,
        onPersist: (newValue: V?) -> Unit = {},
        updater: suspend (oldValue: V?) -> V?,
    ) {
        updateAndGet(
            key = key,
            updater = updater,
            get = { store.get(key) },
            persist = { newValue ->
                if (persistEnabled) store.persist(key, newValue).also { onPersist(newValue) }
                else null
            },
        )
    }

    suspend fun write(
        key: K,
        value: V?,
        persistEnabled: Boolean = true,
        onPersist: (newValue: V?) -> Unit = {},
    ) {
        updateAndGet(
            key = key,
            updater = { value },
            get = { null }, // there may be a value saved in db, but we don't need it
            persist = { newValue ->
                if (persistEnabled) store.persist(key, newValue).also { onPersist(newValue) }
                else null
            },
        )
    }

    protected suspend fun updateAndGet(
        key: K,
        updater: (suspend (oldValue: V?) -> V?)?,
        get: suspend () -> V?,
        persist: suspend (newValue: V?) -> StateFlow<Boolean>?,
    ): StateFlow<V?> {
        val result = _values.updateAndGet { oldValues ->
            val existingCacheValue = oldValues[key]
            if (existingCacheValue == null) {
                log.trace { "$name: no cache hit for key $key" }
                val retrievedValue = get()
                val newValue = if (updater != null) updater(retrievedValue) else retrievedValue
                val persisted = if (updater != null) persist(newValue) else null
                val persistedFlows = MutableStateFlow(setOfNotNull(persisted))
                val removeTime = MutableStateFlow(expireDuration)
                val newStateFlowValue: MutableStateFlow<V?> = MutableStateFlow(newValue)
                val subscriptionCountFlow = newStateFlowValue.subscriptionCount
                val newCacheValue = CoroutineCacheValue(
                    value = newStateFlowValue,
                    removeTimer = removeTime,
                    persisted = persistedFlows,
                    removerJob = launchRemoveFromCacheJob(
                        key = key,
                        subscriptionCountFlow = subscriptionCountFlow,
                        removeTimerFlow = removeTime,
                        persistedFlows = persistedFlows
                    )
                )
                (oldValues + (key to newCacheValue))
                    .also { indexes.value.forEach { index -> index.onPut(key) } }
            } else {
                if (updater != null) {
                    val newValue = existingCacheValue.value.updateAndGet { updater(it) }
                    persist(newValue)?.let { persisted ->
                        existingCacheValue.persisted.update { it + persisted }
                    }
                }
                oldValues
            }
        }[key]
        checkNotNull(result)
        result.removerJob.value // starts the job
        return result.value
    }

    protected val infiniteCache = expireDuration.isInfinite()
    private fun launchRemoveFromCacheJob(
        key: K,
        subscriptionCountFlow: StateFlow<Int>,
        removeTimerFlow: StateFlow<Duration>,
        persistedFlows: StateFlow<Set<StateFlow<Boolean>>>,
    ): Lazy<Job> = lazy {
        cacheScope.launch {
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
                    if (subscriptionCount == 0) {
                        getSubscriptionCount(key).first { it == 0 }
                        log.trace { "$name: remove value from cache with key $key" }
                        _values.updateAndGet {
                            (it - key)
                                .also { indexes.value.forEach { index -> index.onRemove(key) } }
                        }
                    }
                }
        }
    }

    private suspend fun getSubscriptionCount(key: K): Flow<Int> {
        val indexesValue = indexes.value
        return if (indexesValue.isEmpty()) flowOf(0)
        else combine(indexesValue.map { it.getSubscriptionCount(key) }) { subscriptionCounts ->
            subscriptionCounts.sum()
        }
    }
}