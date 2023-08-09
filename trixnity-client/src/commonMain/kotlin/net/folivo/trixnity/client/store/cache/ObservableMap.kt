package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class ObservableMap<K, V>(
    coroutineScope: CoroutineScope
) {
    private val valuesMutex = Mutex()
    private val _values = mutableMapOf<K, V>()

    val indexes = MutableStateFlow(listOf<ObservableMapIndex<K>>())

    private val changeSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        changeSignal.tryEmit(Unit)
    }

    val values = changeSignal
        .map { valuesMutex.withLock { _values.toMap() } }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

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
                    changeSignal.emit(Unit)
                    indexes.value.forEach { index -> index.onPut(key) }
                }

                is CompareAndSetResult.OnRemove -> {
                    changeSignal.emit(Unit)
                    indexes.value.forEach { index -> index.onRemove(key) }
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
        changeSignal.emit(Unit)
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