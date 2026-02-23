package de.connect2x.trixnity.idb.utils

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import web.scheduling.queueMicrotask
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
internal class IDBDispatcher : CoroutineDispatcher(), Delay {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queueMicrotask { block.run() }
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long, continuation: CancellableContinuation<Unit>
    ) {
        error("delay is not supported inside a IDB transaction")
    }
}
