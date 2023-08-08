package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ObservableSet<T>(
    coroutineScope: CoroutineScope,
    initialValue: Set<T> = setOf(),
) {
    private val mutex = Mutex()
    private val _values = initialValue.toMutableSet()

    private val changeSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        changeSignal.tryEmit(Unit)
    }

    val values = changeSignal
        .map { mutex.withLock { _values.toSet() } }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed())

    suspend fun add(element: T): Boolean = mutex.withLock {
        _values.add(element)
            .also { if (it) changeSignal.emit(Unit) }
    }

    suspend fun addAll(elements: Collection<T>) = mutex.withLock {
        _values.addAll(elements)
            .also { if (it) changeSignal.emit(Unit) }
    }

    suspend fun remove(element: T): Boolean = mutex.withLock {
        _values.remove(element)
            .also { if (it) changeSignal.emit(Unit) }
    }

    suspend fun size(): Int = mutex.withLock { _values.size }
}