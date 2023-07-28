package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

private data class CoroutineCacheValue<T>(
    val value: MutableStateFlow<T>,
    val persisted: MutableStateFlow<Set<StateFlow<Boolean>>>,
)

/**
 * The actual source and sink of the data to be cached. This could be any database.
 */
interface CoroutineCacheStore<K, V> {
    /**
     * Retrieve value from store.
     */
    suspend fun get(key: K): V?

    /**
     * Save value to store.
     *
     * @return A [StateFlow] which indicates, when the value has been persisted (keyword asynchronous cache)
     */
    suspend fun persist(key: K, value: V?): StateFlow<Boolean>?

    /**
     * Delete all values from store.
     */
    suspend fun deleteAll()
}

/**
 * An index to track which entries have been added to or removed from the cache.
 */
interface ObservableMapIndex<K> {
    /**
     * Called, when an entry is added to the cache.
     */
    suspend fun onPut(key: K)

    /**
     * Called, when an entry is removed from the cache.
     */
    suspend fun onRemove(key: K)

    /**
     * Called, when all entries are removed from the cache.
     */
    suspend fun onRemoveAll()

    /**
     * Get the subscription count on an index entry, which uses an entry of the cache.
     */
    suspend fun getSubscriptionCount(key: K): StateFlow<Int>
}

/**
 * Base class to create a coroutine and [StateFlow] based cache.
 *
 * @param name The name is just used for logging.
 * @param cacheScope A long living [CoroutineScope] to spawn coroutines, which remove entries from cache when not used anymore.
 * @param expireDuration Duration to wait until entries from cache are when not used anymore.
 */
open class CoroutineCache<K, V, S : CoroutineCacheStore<K, V>>(
    protected val name: String,
    protected val store: S,
    protected val cacheScope: CoroutineScope,
    protected val expireDuration: Duration = 1.minutes,
) {
    private val _values = ObservableMap<K, CoroutineCacheValue<V?>>(cacheScope)
    val values: SharedFlow<Map<K, StateFlow<V?>>> = _values.values
        .map { value -> value.mapValues { it.value.value.asStateFlow() } }
        .shareIn(cacheScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    fun addIndex(index: ObservableMapIndex<K>) {
        _values.indexes.update { it + index }
    }

    suspend fun clear() {
        _values.removeAll()
    }

    suspend fun deleteAll() {
        store.deleteAll()
        _values.removeAll()
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
        var removeFromCacheJobParameter: RemoveFromCacheJobParameter?
        val result = _values.update(key) { existingCacheValue ->
            if (existingCacheValue == null) {
                log.trace { "$name: no cache hit for key $key" }
                val retrievedValue = get()
                val newValue = if (updater != null) updater(retrievedValue) else retrievedValue
                val persisted = if (updater != null) persist(newValue) else null
                val persistedFlows = MutableStateFlow(setOfNotNull(persisted))
                val newStateFlowValue: MutableStateFlow<V?> = MutableStateFlow(newValue)
                val subscriptionCountFlow = newStateFlowValue.subscriptionCount
                removeFromCacheJobParameter = RemoveFromCacheJobParameter(
                    subscriptionCount = subscriptionCountFlow,
                    persisted = persistedFlows
                )
                val newCacheValue = CoroutineCacheValue(
                    value = newStateFlowValue,
                    persisted = persistedFlows,
                )
                newCacheValue
            } else {
                if (updater != null) {
                    val newValue = existingCacheValue.value.updateAndGet { updater(it) }
                    persist(newValue)?.let { persisted ->
                        existingCacheValue.persisted.update { it + persisted }
                    }
                }
                removeFromCacheJobParameter = null
                existingCacheValue
            }
        }
        checkNotNull(result)
        removeFromCacheJobParameter?.also { launchRemoveFromCacheJob(key, it) }
        return result.value
    }

    protected val infiniteCache = expireDuration.isInfinite()

    private data class RemoveFromCacheJobParameter(
        val subscriptionCount: StateFlow<Int>,
        val persisted: MutableStateFlow<Set<StateFlow<Boolean>>>,
    )

    private fun launchRemoveFromCacheJob(
        key: K,
        parameters: RemoveFromCacheJobParameter,
    ) = cacheScope.launch {
        if (infiniteCache.not())
            combine(
                parameters.subscriptionCount,
                parameters.persisted
            ) { subscriptionCount, persisted ->
                subscriptionCount to persisted
            }.collectLatest { (subscriptionCount, persisted) ->
                delay(expireDuration)
                // waiting for the cache value to be written into the repository
                // otherwise cache and repository could get out of sync
                persisted.forEach { persistedFlow ->
                    persistedFlow.first { it }
                }
                if (subscriptionCount == 0) {
                    _values.getIndexSubscriptionCount(key).first { it == 0 }
                    log.trace { "$name: remove value from cache with key $key" }
                    _values.update(key) { null }
                }
            }
    }
}