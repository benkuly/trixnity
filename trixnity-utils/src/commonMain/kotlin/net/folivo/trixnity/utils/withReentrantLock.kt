package net.folivo.trixnity.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
    if (currentCoroutineContext()[ReentrantMutexContextElement] != null) return block()
    return withContext(ReentrantMutexContextElement) {
        withLock { block() }
    }
}

private object ReentrantMutexContextElement : CoroutineContext.Element,
    CoroutineContext.Key<ReentrantMutexContextElement> {
    override val key: CoroutineContext.Key<*> = this
}