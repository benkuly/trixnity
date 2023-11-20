package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class ConcurrentMap<K, V> {
    private val valuesMutex = Mutex()
    private val _values = mutableMapOf<K, V>()

    val indexes = MutableStateFlow(listOf<ObservableMapIndex<K>>())

    sealed interface CompareAndSetResult {
        object TryAgain : CompareAndSetResult
        object OnPut : CompareAndSetResult
        object OnRemove : CompareAndSetResult
        object NothingChanged : CompareAndSetResult
    }

    private suspend fun compareAndSet(key: K, expectedOldValue: V?, newValue: V?): CompareAndSetResult =
        valuesMutex.withLock {
            val oldValue = _values[key]
            when {
                expectedOldValue != oldValue -> CompareAndSetResult.TryAgain
                newValue == null -> {
                    _values.remove(key)
                    CompareAndSetResult.OnRemove
                }

                expectedOldValue != newValue -> {
                    _values[key] = newValue
                    CompareAndSetResult.OnPut
                }

                else -> CompareAndSetResult.NothingChanged
            }
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

    suspend fun get(key: K): V? = valuesMutex.withLock {
        _values[key]
    }

    suspend fun getAll(): Map<K, V> = valuesMutex.withLock {
        _values.toMap()
    }

    suspend fun removeAll() = valuesMutex.withLock {
        _values.clear()
        indexes.value.forEach { index -> index.onRemoveAll() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getIndexSubscriptionCount(key: K): Flow<Int> =
        indexes.flatMapLatest { indexesValue ->
            if (indexesValue.isEmpty()) flowOf(0)
            else combine(indexesValue.map { it.getSubscriptionCount(key) }) { subscriptionCounts ->
                subscriptionCounts.sum()
            }
        }
}