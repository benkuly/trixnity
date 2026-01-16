package de.connect2x.trixnity.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
    val key = ReentrantMutexContextKey(this)
    if (currentCoroutineContext()[key] != null) return block()
    return withContext(ReentrantMutexContextElement(key)) {
        withLock { block() }
    }
}

class ReentrantMutexContextElement(
    override val key: ReentrantMutexContextKey
) : CoroutineContext.Element

data class ReentrantMutexContextKey(
    val mutex: Mutex
) : CoroutineContext.Key<ReentrantMutexContextElement>