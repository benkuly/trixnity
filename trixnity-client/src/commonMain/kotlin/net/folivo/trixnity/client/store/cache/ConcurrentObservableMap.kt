package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.utils.concurrentMutableMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class ConcurrentObservableMap<K, V> {
    private val _values = concurrentMutableMap<K, V>()

    val indexes = MutableStateFlow(listOf<ObservableCacheIndex<K>>())

    sealed interface CompareAndSetResult {
        data object TryAgain : CompareAndSetResult
        data object OnPut : CompareAndSetResult
        data object OnRemove : CompareAndSetResult
        data object NothingChanged : CompareAndSetResult
    }

    private suspend fun compareAndSet(key: K, expectedOldValue: V?, newValue: V?): CompareAndSetResult =
        _values.write {
            val oldValue = get(key)
            when {
                expectedOldValue != oldValue -> CompareAndSetResult.TryAgain
                newValue == null -> {
                    remove(key)
                    CompareAndSetResult.OnRemove
                }

                expectedOldValue != newValue -> {
                    put(key, newValue)
                    CompareAndSetResult.OnPut
                }

                else -> CompareAndSetResult.NothingChanged
            }
        }

    suspend fun getOrPut(key: K, defaultValue: () -> V): V =
        _values.read { get(key) }
            ?: checkNotNull(update(key) { it ?: defaultValue() })

    suspend fun skipPut(key: K) {
        indexes.first().forEach { it.onSkipPut(key) }
    }


    @OptIn(ExperimentalContracts::class)
    suspend fun update(
        key: K,
        updater: suspend (V?) -> V?,
    ): V? {
        contract {
            callsInPlace(updater, InvocationKind.AT_LEAST_ONCE)
        }
        return internalUpdate(key, updater = updater)
    }

    suspend fun remove(key: K, stale: Boolean = false) {
        internalUpdate(key, stale) { null }
    }

    @OptIn(ExperimentalContracts::class)
    private suspend fun internalUpdate(
        key: K,
        stale: Boolean = false,
        updater: suspend (V?) -> V?,
    ): V? {
        contract {
            callsInPlace(updater, InvocationKind.AT_LEAST_ONCE)
        }
        // inspired by [MutableStateFlow::update]
        while (true) {
            val oldValue = get(key)
            val newValue = updater(oldValue)
            val compareAndSetResult = compareAndSet(key, oldValue, newValue)
            when (compareAndSetResult) {
                is CompareAndSetResult.TryAgain,
                is CompareAndSetResult.NothingChanged -> {
                }

                is CompareAndSetResult.OnPut -> {
                    indexes.value.forEach { index -> index.onPut(key) }
                }

                is CompareAndSetResult.OnRemove -> {
                    indexes.value.forEach { index -> index.onRemove(key, stale) }
                }
            }
            if (compareAndSetResult !is CompareAndSetResult.TryAgain) {
                return newValue
            }
        }
    }

    suspend fun get(key: K): V? = _values.read { get(key) }

    suspend fun getAll(): Map<K, V> = _values.read { toMap() }

    internal suspend fun <R> internalRead(reader: suspend Map<K, V>.() -> R) = _values.read(reader)

    suspend fun removeAll() = _values.write {
        clear()
        indexes.value.forEach { index -> index.onRemoveAll() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getIndexSubscriptionCount(key: K): Int =
        indexes.first().sumOf { it.getSubscriptionCount(key) }
}