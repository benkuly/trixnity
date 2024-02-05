package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.utils.concurrentOf

class ConcurrentObservableSet<T>(
    initialValue: Set<T> = setOf(),
) {
    private val _values = concurrentOf<Set<T>, MutableSet<T>> { initialValue.toMutableSet() }

    private val changeSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        changeSignal.tryEmit(Unit)
    }

    val values = changeSignal
        .conflate()
        .map { _values.read { toSet() } }

    suspend fun add(element: T): Boolean = _values.write {
        add(element)
    }.also { if (it) changeSignal.emit(Unit) }

    suspend fun remove(element: T): Boolean = _values.write {
        remove(element)
    }.also { if (it) changeSignal.emit(Unit) }

    suspend fun removeAll() = _values.write {
        clear()
    }.also { changeSignal.emit(Unit) }

    suspend fun size(): Int = _values.read { size }
}