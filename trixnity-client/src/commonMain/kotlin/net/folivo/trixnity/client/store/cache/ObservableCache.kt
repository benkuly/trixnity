package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

internal sealed interface CacheValue<T> {
    class Init<T> : CacheValue<T>

    @JvmInline
    value class Value<T>(val value: T) : CacheValue<T>

    fun valueOrNull() = when (this) {
        is Init -> null
        is Value -> value
    }
}

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
    values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
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
    private val values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
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
        get: (suspend () -> V?)? = null,
        persist: (suspend (newValue: V?) -> Unit)? = null,
    ): Flow<V?> {
        val cacheEntry = values.getOrPut(key) {
            MutableStateFlow<CacheValue<V?>>(CacheValue.Init())
                .also { log.trace { "$name: no cache hit for key $key" } }
        }
        cacheEntry.first() // resets expire duration by increasing subscription count for a moment
        val newValue = cacheEntry.updateAndGet {
            val oldValue = when (it) {
                is CacheValue.Init -> get?.invoke()
                is CacheValue.Value -> it.value
            }
            val newValue = if (updater != null) updater(oldValue) else oldValue
            if (persist != null && (oldValue != newValue || get == null)) persist(newValue)
            CacheValue.Value(newValue)
        }.valueOrNull()
        if (removeFromCacheOnNull && updater != null && newValue == null) {
            log.trace { "$name: remove value from cache with key $key because it is stale and is allowed to remove (will never be not-null again)" }
            values.remove(key, true)
        }
        return cacheEntry.filterIsInstance<CacheValue.Value<V?>>().map { it.value }
    }
}

private class RemoverJobExecutingIndex<K, V>(
    private val name: String,
    private val values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>>,
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
                    value.subscriptionCount,
                    values.getIndexSubscriptionCount(key),
                ) { subscriptionCount, indexSubscriptionCount ->
                    subscriptionCount to indexSubscriptionCount
                }.collectLatest { (subscriptionCount, indexSubscriptionCount) ->
                    delay(expireDuration)
                    val stale = value.value.valueOrNull() == null
                    log.trace { "$name: remover job check for key $key (subscriptionCount=$subscriptionCount, indexSubscriptionCount=$indexSubscriptionCount, stale=$stale)" }
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