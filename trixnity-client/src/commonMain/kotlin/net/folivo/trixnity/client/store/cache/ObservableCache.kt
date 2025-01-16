package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.utils.concurrentMutableMap
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
internal interface ObservableCacheIndex<K> {
    /**
     * Called, when an entry is added to the cache.
     */
    suspend fun onPut(key: K)

    /**
     * Called, when an entry has skipped the cache. Skipping is done, when there is no subscriber of a cache entry.
     */
    suspend fun onSkipPut(key: K)

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
    suspend fun getSubscriptionCount(key: K): Int

    suspend fun collectStatistic(): ObservableCacheIndexStatistic?
}

/**
 * Base class to create a coroutine and [StateFlow] based cache.
 *
 * @param name The name is just used for logging.
 * @param cacheScope A long living [CoroutineScope] to spawn coroutines, which remove entries from cache when not used anymore.
 * @param expireDuration Duration to wait until entries from cache are when not used anymore.
 */
internal open class ObservableCache<K : Any, V, S : ObservableCacheStore<K, V>>(
    val name: String,
    protected val store: S,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
    private val removeFromCacheOnNull: Boolean = false,
    private val values: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>> = ConcurrentObservableMap(),
) {
    private val removerIndex =
        if (expireDuration.isInfinite().not()) {
            RemoverJobExecutingIndex(name, values, clock, expireDuration)
                .also { addIndex(it) }
        } else null

    init {
        if (removerIndex != null)
            cacheScope.launch {
                while (isActive) {
                    delay(2.seconds)
                    removerIndex.invalidateCache()
                }
            }
    }

    fun addIndex(index: ObservableCacheIndex<K>) {
        values.indexes.update { it + index }
    }

    suspend fun invalidate() {
        removerIndex?.invalidateCache()
    }

    suspend fun clear() {
        values.removeAll()
    }

    suspend fun deleteAll() {
        store.deleteAll()
        clear()
    }

    fun get(key: K): Flow<V?> = flow {
        val cacheEntry =
            values.getOrPut(key) {
                log.trace { "$name (get): no cache hit for key $key" }
                MutableStateFlow<CacheValue<V?>>(CacheValue.Init())
            }.also {
                it.get { store.get(key) }
            }
        emitAll(cacheEntry.filterIsInstance<CacheValue.Value<V?>>().map { it.value })
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.get(
        noinline get: (suspend () -> V?),
    ): V? {
        while (true) {
            val oldRawValue = value
            val oldValue = when (oldRawValue) {
                is CacheValue.Init -> get()
                is CacheValue.Value -> oldRawValue.value
            }
            val newRawValue = CacheValue.Value(oldValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                return oldValue
            }
        }
    }

    suspend fun set(
        key: K,
        value: V?,
        persistEnabled: Boolean = true,
        onPersist: (newValue: V?) -> Unit = {},
    ) {
        val persist: (suspend (V?) -> Unit)? =
            if (persistEnabled) {
                { newValue -> store.persist(key, newValue).also { onPersist(newValue) } }
            } else null

        if (persist != null
            && values.get(key) == null
            && values.getIndexSubscriptionCount(key) == 0
        ) {
            log.trace { "$name (set): skipped cache and persist directly because there is no cache entry or subscriber for key $key" }
            values.skipPut(key)
            persist(value)

            val cacheEntry = values.get(key)
            if (cacheEntry != null) {
                log.trace { "$name (set): skipped cache but found a cache entry and therefore filling it for key $key" }
                cacheEntry.set(value, null, force = true)
                possiblyRemoveFromCache(value, key)
            } else {
                log.trace { "$name (set): skipped cache successful for key $key" }
            }
        } else {
            val cacheEntry =
                values.getOrPut(key) {
                    log.trace { "$name (set): no cache hit for key $key" }
                    MutableStateFlow<CacheValue<V?>>(CacheValue.Value(value))
                }
            cacheEntry.set(value, persist)
            possiblyRemoveFromCache(value, key)
        }
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.set(
        newValue: V?,
        noinline persist: (suspend (newValue: V?) -> Unit)? = null,
        force: Boolean = false,
    ) {
        while (true) {
            val oldRawValue = value
            // prefer cache value (when not going to be nulled)
            if (!force && newValue != null && persist == null && oldRawValue is CacheValue.Value) return
            val newRawValue = CacheValue.Value(newValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                if (persist != null && (oldRawValue.valueOrNull() != newValue)) persist(newValue)
                return
            }
        }
    }

    suspend fun update(
        key: K,
        persistEnabled: Boolean = true,
        onPersist: (newValue: V?) -> Unit = {},
        updater: suspend (oldValue: V?) -> V?,
    ) {
        val persist: (suspend (V?) -> Unit)? =
            if (persistEnabled) {
                { newValue -> store.persist(key, newValue).also { onPersist(newValue) } }
            } else null

        val cacheEntry =
            values.getOrPut(key) {
                log.trace { "$name (update): no cache hit for key $key" }
                MutableStateFlow<CacheValue<V?>>(CacheValue.Init())
            }
        val value = cacheEntry.updateAndGet(updater, { store.get(key) }, persist)
        possiblyRemoveFromCache(value, key)
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.updateAndGet(
        noinline updater: (suspend (oldValue: V?) -> V?),
        noinline get: (suspend () -> V?),
        noinline persist: (suspend (newValue: V?) -> Unit)? = null,
    ): V? {
        while (true) {
            val oldRawValue = value
            val oldValue = when (oldRawValue) {
                is CacheValue.Init -> get()
                is CacheValue.Value -> oldRawValue.value
            }
            val newValue = updater(oldValue)
            val newRawValue = CacheValue.Value(newValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                if (persist != null && (oldValue != newValue)) persist(newValue)
                return newValue
            }
        }
    }

    private suspend fun possiblyRemoveFromCache(value: V?, key: K) {
        if (removeFromCacheOnNull && value == null) {
            log.trace { "$name: remove value from cache with key $key because it is stale and is allowed to remove (will never be not-null again)" }
            values.remove(key, true)
        }
    }

    internal suspend fun collectStatistic(): ObservableCacheStatistic {
        val (all, subscribed) = values.internalRead {
            count() to values.count { it.subscriptionCount.value > 0 }
        }
        return ObservableCacheStatistic(
            name = name,
            all = all,
            subscribed = subscribed,
            indexes = values.indexes.value.mapNotNull { it.collectStatistic() }
        )
    }
}

private class RemoverJobExecutingIndex<K : Any, V>(
    private val name: String,
    private val cacheValues: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>>,
    private val clock: Clock,
    private val expireDuration: Duration,
) : ObservableCacheIndex<K> {
    private val activityCount = MutableStateFlow(0)
    private val removeAfter = concurrentMutableMap<K, Instant>()

    suspend fun invalidateCache() {
        if (removeAfter.read { isNotEmpty() }) {
            log.trace { "$name: start invalidate cache" }
            activityCount.first { it == 0 }
            val now = clock.now()
            val (unsubscribed, subscribed) = removeAfter.read {
                entries.partition { (key, _) ->
                    (cacheValues.get(key)?.subscriptionCount?.value ?: 0) == 0
                            && (
                            cacheValues.get(key)?.value?.valueOrNull() == null
                                    || cacheValues.getIndexSubscriptionCount(key) == 0
                            )
                }
            }
            coroutineScope {
                launch {
                    val nextExpiration = now + expireDuration
                    log.trace { "$name: update invalidation to $nextExpiration for ${subscribed.size} entries" }
                    removeAfter.write {
                        putAll(subscribed.map { it.key to nextExpiration })
                    }
                }
                launch {
                    log.trace { "$name: check invalidation at $now for ${unsubscribed.size} entries" }
                    unsubscribed.forEach { (key, value) ->
                        if (now > value) {
                            val cacheValue = cacheValues.get(key)
                            if (cacheValue != null) {
                                val stale = cacheValue.value.valueOrNull() == null
                                log.trace { "$name: remove value from cache with key $key (stale=$stale)" }
                                cacheValues.remove(key, stale)
                            }
                        }
                    }
                }
            }
            log.trace { "$name: finished invalidate cache" }
        }
    }

    override suspend fun onPut(key: K) {
        removeAfter.write { put(key, clock.now() + expireDuration) }
    }

    override suspend fun onSkipPut(key: K) {}

    override suspend fun onRemove(key: K, stale: Boolean) {
        removeAfter.write { remove(key) }
    }

    override suspend fun onRemoveAll() {
        removeAfter.write { clear() }
    }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null

    override suspend fun getSubscriptionCount(key: K): Int = 0
}