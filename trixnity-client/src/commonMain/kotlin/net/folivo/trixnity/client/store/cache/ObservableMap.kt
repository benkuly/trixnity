package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        .onEach { println("ObservableMap: $it") }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    private suspend fun compareAndSet(key: K, expectedOldValue: V?, newValue: V?): Boolean = valuesMutex.withLock {
        val oldValue = _values[key]
        when {
            expectedOldValue != oldValue -> false
            newValue == null -> {
                _values.remove(key)
                changeSignal.emit(Unit)
                indexes.value.forEach { index -> index.onRemove(key) }
                true
            }

            expectedOldValue != newValue -> {
                _values[key] = newValue
                changeSignal.emit(Unit)
                indexes.value.forEach { index -> index.onPut(key) }
                true
            }

            else -> true
        }
    }

    suspend fun update(
        key: K,
        updater: suspend (V?) -> V?
    ): V? {
        while (true) {
            val oldValue = get(key)
            val newValue = updater(oldValue)
            if (compareAndSet(key, oldValue, newValue)) {
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

    suspend fun getIndexSubscriptionCount(key: K): Flow<Int> {
        val indexesValue = indexes.value
        return if (indexesValue.isEmpty()) flowOf(0)
        else combine(indexesValue.map { it.getSubscriptionCount(key) }) { subscriptionCounts ->
            subscriptionCounts.sum()
        }
    }
}