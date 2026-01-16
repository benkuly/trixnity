package de.connect2x.trixnity.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface Concurrent<R, W : R> {
    suspend fun <V> write(writer: suspend W.() -> V): V

    suspend fun <V> read(reader: suspend R.() -> V): V
}

class ConcurrentImpl<R, W : R>(
    constructor: () -> W
) : Concurrent<R, W> {
    companion object {
        internal const val LEFT = true
        internal const val RIGHT = false
    }

    private val writeMutex = Mutex()
    private var switch = LEFT

    private val left = constructor()
    private val right = constructor()

    private val leftReaderCount = MutableStateFlow(0)
    private val rightReaderCount = MutableStateFlow(0)

    private fun writeSide(switchValue: Boolean = switch) = if (switchValue == LEFT) left else right
    private fun readSide(switchValue: Boolean = switch) = if (switchValue == LEFT) right else left
    private fun readerCount(switchValue: Boolean = switch) =
        if (switchValue == LEFT) rightReaderCount else leftReaderCount

    override suspend fun <V> write(writer: suspend W.() -> V): V = writeMutex.withLock {
        val writeSide1 = writeSide()
        val result1 = writer(writeSide1)

        switch = !switch
        readerCount(!switch).first { it == 0 }

        val writeSide2 = writeSide()
        val result2 = writer(writeSide2)

        if (result1 === writeSide1 || result2 === writeSide2) throw IllegalArgumentException("writer must not leak internal data")
        result2
    }

    override suspend fun <V> read(reader: suspend R.() -> V): V {
        while (true) {
            val currentSwitchValue = switch
            val readerCount = readerCount(currentSwitchValue)
            readerCount.update { it + 1 }
            val readSide = readSide(currentSwitchValue)
            try {
                if (currentSwitchValue == switch) {
                    val result = reader(readSide)
                    if (result === readSide) throw IllegalArgumentException("reader must not leak internal data")
                    return result
                }
            } finally {
                readerCount.update { it - 1 }
            }
        }
    }
}

fun <R, W : R> concurrentOf(constructor: () -> W): Concurrent<R, W> = ConcurrentImpl(constructor)