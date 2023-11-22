package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConcurrentObservableSet<T>(
    initialValue: Set<T> = setOf(),
) {
    private val valuesMutex = Mutex()
    private val _values = initialValue.toMutableSet()

    private val changeSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        changeSignal.tryEmit(Unit)
    }

    val values = changeSignal
        .conflate()
        .map { valuesMutex.withLock { _values.toSet() } }

    suspend fun add(element: T): Boolean = valuesMutex.withLock {
        _values.add(element)
            .also { if (it) changeSignal.emit(Unit) }
    }

    suspend fun remove(element: T): Boolean = valuesMutex.withLock {
        _values.remove(element)
            .also { if (it) changeSignal.emit(Unit) }
    }

    suspend fun removeAll() = valuesMutex.withLock {
        _values.clear()
        changeSignal.emit(Unit)
    }

    suspend fun size(): Int = valuesMutex.withLock { _values.size }
}