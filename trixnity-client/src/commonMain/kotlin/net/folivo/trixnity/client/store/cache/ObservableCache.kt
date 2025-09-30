package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.concurrentMutableMap
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val log = KotlinLogging.logger("net.folivo.trixnity.client.store.cache.ObservableCache")

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
                MutableStateFlow(CacheValue.Init())
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
        withCacheTransaction { cacheTransaction ->
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

                cacheTransaction.onCommitActions.write {
                    add {
                        val cacheEntry = values.get(key)
                        if (cacheEntry != null) {
                            log.trace { "$name (set): skipped cache but found a cache entry and therefore filling it for key $key" }
                            cacheEntry.set(key, value, cacheTransaction, null, forceCacheOnly = true)
                                ?.also { (oldValue, newValue) ->
                                    possiblyRemoveFromCache(oldValue, newValue, key)
                                }
                        }
                    }
                }
            } else {
                val cacheEntry =
                    values.getOrPut(key) {
                        log.trace { "$name (set): no cache hit for key $key" }
                        MutableStateFlow(CacheValue.Init())
                    }
                cacheEntry.set(key, value, cacheTransaction, persist)?.also { (oldValue, newValue) ->
                    possiblyRemoveFromCache(oldValue, newValue, key)
                }
            }
        }
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.set(
        key: K,
        newValue: V?,
        cacheTransaction: CacheTransaction,
        noinline persist: (suspend (newValue: V?) -> Unit)? = null,
        forceCacheOnly: Boolean = false,
    ): ValueUpdate<V>? {
        while (true) {
            val oldRawValue = value
            val oldValue = oldRawValue.valueOrNull()
            if (forceCacheOnly.not() && newValue != null && persist == null && oldRawValue is CacheValue.Value) {
                log.trace { "$name (set): skip cache set for key $key because it is already cached" }
                return null
            }
            val newRawValue = CacheValue.Value(newValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                cacheTransaction.onRollbackActions.write {
                    add {
                        log.trace { "$name (set): rollback cache update for key $key" }
                        if (compareAndSet(newRawValue, oldRawValue).not()) {
                            log.warn { "$name (set): cache entry has been updated outside of this transaction. Force rollback for key $key" }
                            value = oldRawValue
                        }
                    }
                }
                if (forceCacheOnly.not() && persist != null && (oldValue != newValue)) {
                    persist(newValue)
                } else {
                    log.trace { "$name (set): skip cache persist for key $key because there was no change" }
                }
                return ValueUpdate(oldValue, newValue)
            }
        }
    }

    suspend fun update(
        key: K,
        persistEnabled: Boolean = true,
        onPersist: (newValue: V?) -> Unit = {},
        updater: suspend (oldValue: V?) -> V?,
    ) {
        withCacheTransaction { cacheTransaction ->
            val persist: (suspend (V?) -> Unit)? =
                if (persistEnabled) {
                    { newValue -> store.persist(key, newValue).also { onPersist(newValue) } }
                } else null

            val cacheEntry =
                values.getOrPut(key) {
                    log.trace { "$name (update): no cache hit for key $key" }
                    MutableStateFlow(CacheValue.Init())
                }
            val (oldValue, newValue) = cacheEntry.updateAndGet(
                key,
                updater,
                cacheTransaction,
                { store.get(key) },
                persist
            )
            possiblyRemoveFromCache(oldValue, newValue, key)
        }
    }

    private suspend inline fun <V> MutableStateFlow<CacheValue<V?>>.updateAndGet(
        key: K,
        noinline updater: (suspend (oldValue: V?) -> V?),
        cacheTransaction: CacheTransaction,
        noinline get: (suspend () -> V?),
        noinline persist: (suspend (newValue: V?) -> Unit)? = null,
    ): ValueUpdate<V> {
        while (true) {
            val oldRawValue = value
            val oldValue = when (oldRawValue) {
                is CacheValue.Init -> get()
                is CacheValue.Value -> oldRawValue.value
            }
            val newValue = updater(oldValue)
            val newRawValue = CacheValue.Value(newValue)
            if (compareAndSet(oldRawValue, newRawValue)) {
                cacheTransaction.onRollbackActions.write {
                    add {
                        log.trace { "$name (update): rollback cache update for key $key" }
                        if (compareAndSet(newRawValue, oldRawValue).not()) {
                            log.warn { "$name (update): cache entry has been updated outside of this transaction. Force rollback for key $key" }
                            value = oldRawValue
                        }
                    }
                }
                if (persist != null && (oldValue != newValue)) persist(newValue)
                return ValueUpdate(oldValue, newValue)
            }
        }
    }

    private suspend fun possiblyRemoveFromCache(oldValue: V?, newValue: V?, key: K) {
        if (removeFromCacheOnNull && newValue == null && oldValue != null) {
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

internal class RemoverJobExecutingIndex<K : Any, V>(
    private val name: String,
    private val cacheValues: ConcurrentObservableMap<K, MutableStateFlow<CacheValue<V?>>>,
    private val clock: Clock,
    private val expireDuration: Duration,
) : ObservableCacheIndex<K> {
    private val removeAfter = concurrentMutableMap<K, Instant>()

    suspend fun invalidateCache() {
        if (removeAfter.read { isNotEmpty() }) {
            log.trace { "$name: start invalidate cache" }
            val now = clock.now()
            val (unsubscribed, subscribed) = removeAfter.read {
                val partition = entries.partition { (key, _) ->
                    val cacheValue = cacheValues.get(key)
                    (cacheValue?.subscriptionCount?.value ?: 0) == 0
                            && (
                            cacheValue?.value?.valueOrNull() == null
                                    || cacheValues.getIndexSubscriptionCount(key) == 0
                            )
                }
                // This is needed, because using Map.Entry from a mutable map is not safe to use.
                partition.first.map { it.key to it.value } to partition.second.map { it.key }
            }
            coroutineScope {
                launch {
                    val nextExpiration = now + expireDuration
                    log.trace { "$name: update invalidation to $nextExpiration for ${subscribed.size} entries" }
                    removeAfter.write {
                        putAll(subscribed.map { it to nextExpiration })
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

private data class ValueUpdate<V>(
    val oldValue: V?,
    val newValue: V?
)