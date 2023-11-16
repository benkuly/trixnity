package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

internal data class ObservableCacheValue<T>(
    val value: MutableStateFlow<T>,
    val resetExpireDuration: MutableSharedFlow<Unit>,
)

/**
 * The actual source and sink of the data to be cached. This could be any database.
 */
internal interface ObservableCacheStore<K, V> {
    /**
     * Retrieve value from store.
     */
    suspend fun get(key: K): V?

    /**
     * Save value to store.
     */
    suspend fun persist(key: K, value: V?)

    /**
     * Delete all values from store.
     */
    suspend fun deleteAll()
}

/**
 * An index to track which entries have been added to or removed from the cache.
 */
internal interface ObservableMapIndex<K> {
    /**
     * Called, when an entry is added to the cache.
     */
    suspend fun onPut(key: K)

    /**
     * Called, when an entry is removed from the cache.
     *
     * @param stale means that the value has been deleted from the database. It is only set to true, when no-one listens to this specific key.
     */
    suspend fun onRemove(key: K, stale: Boolean)

    /**
     * Called, when all entries are removed from the cache.
     */
    suspend fun onRemoveAll()

    /**
     * Get the subscription count on an index entry, which uses an entry of the cache.
     */
    suspend fun getSubscriptionCount(key: K): Flow<Int>
}

/**
 * Base class to create a coroutine and [StateFlow] based cache.
 *
 * @param name The name is just used for logging.
 * @param cacheScope A long living [CoroutineScope] to spawn coroutines, which remove entries from cache when not used anymore.
 * @param expireDuration Duration to wait until entries from cache are when not used anymore.
 * @param removeFromCacheOnNull removes an entry from the cache, when the value is null.
 */
internal open class ObservableCache<K, V, S : ObservableCacheStore<K, V>>(
    name: String,
    protected val store: S,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    removeFromCacheOnNull: Boolean = false,
    values: ConcurrentMap<K, ObservableCacheValue<V?>> = ConcurrentMap(),
) : ObservableCacheBase<K, V>(
    name = name,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    removeFromCacheOnNull = removeFromCacheOnNull,
    values = values,
) {
    suspend fun deleteAll() {
        store.deleteAll()
        clear()
    }

    fun read(key: K): Flow<V?> = flow {
        emitAll(
            updateAndGet(
                key = key,
                get = { store.get(key) },
            )?.value ?: MutableStateFlow(null)
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
            get = { value },
            persist = { newValue ->
                if (persistEnabled) store.persist(key, newValue).also { onPersist(newValue) }
            },
        )
    }
}

internal open class ObservableCacheBase<K, V>(
    protected val name: String,
    protected val cacheScope: CoroutineScope,
    protected val expireDuration: Duration = 1.minutes,
    protected val removeFromCacheOnNull: Boolean = false,
    private val values: ConcurrentMap<K, ObservableCacheValue<V?>> = ConcurrentMap(),
) {
    init {
        addIndex(RemoverJobExecutingIndex(name, values, cacheScope, expireDuration))
    }

    fun addIndex(index: ObservableMapIndex<K>) {
        values.indexes.update { it + index }
    }

    suspend fun clear() {
        values.removeAll()
    }

    protected suspend fun updateAndGet(
        key: K,
        updater: (suspend (oldValue: V?) -> V?)? = null,
        get: (suspend () -> V?),
        persist: (suspend (newValue: V?) -> Unit)? = null,
    ): ObservableCacheValue<V?>? {
        val result = values.update(key) { existingCacheValue ->
            if (existingCacheValue == null) {
                log.trace { "$name: no cache hit for key $key" }
                val retrievedValue = get()
                val newCacheValue = ObservableCacheValue(
                    value = MutableStateFlow(retrievedValue),
                    resetExpireDuration = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_OLDEST)
                        .also { it.emit(Unit) },
                )
                newCacheValue
            } else {
                existingCacheValue.resetExpireDuration.emit(Unit)
                existingCacheValue
            }
        }
        checkNotNull(result)
        if (updater != null) {
            val newValue = result.value.updateAndGet { oldValue ->
                updater(oldValue)
                    .also { if (persist != null) persist(it) }
            }
            if (removeFromCacheOnNull && newValue == null) {
                values.remove(key, true)
                return null
            }
        }
        return result
    }
}

private class RemoverJobExecutingIndex<K, V>(
    private val name: String,
    private val values: ConcurrentMap<K, ObservableCacheValue<V?>>,
    private val cacheScope: CoroutineScope,
    private val expireDuration: Duration = 1.minutes,
) : ObservableMapIndex<K> {
    private val infiniteCache = expireDuration.isInfinite()
    override suspend fun onPut(key: K) {
        if (infiniteCache.not()) {
            val value = values.get(key) ?: return
            cacheScope.launch {
                log.trace { "$name: launch remover job for key $key" }
                combine(
                    value.resetExpireDuration,
                    value.value.subscriptionCount,
                    values.getIndexSubscriptionCount(key),
                ) { _, subscriptionCount, indexSubscriptionCount ->
                    subscriptionCount to indexSubscriptionCount
                }.collectLatest { (subscriptionCount, indexSubscriptionCount) ->
                    delay(expireDuration)
                    val stale = value.value.value == null
                    // indexSubscriptionCount currently means, that a collection of entries is subscribed.
                    // Therefore, it's okay to remove cache entries. The index would just update its list.
                    if (subscriptionCount == 0 && (stale || indexSubscriptionCount == 0)) {
                        log.trace { "$name: remove value from cache with key $key" }
                        values.remove(key, stale)
                    }
                }
            }
        }
    }

    override suspend fun onRemove(key: K, stale: Boolean) {}
    override suspend fun onRemoveAll() {}
    override suspend fun getSubscriptionCount(key: K): StateFlow<Int> = MutableStateFlow(0)
}