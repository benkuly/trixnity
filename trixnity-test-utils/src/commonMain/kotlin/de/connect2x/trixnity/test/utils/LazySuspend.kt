package de.connect2x.trixnity.test.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KProperty

interface LazySuspend<out T> {
    val value: T
    val isCompleted: Boolean

    fun <U> map(transformer: (T) -> U): LazySuspend<U>
}

operator fun <T> LazySuspend<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

internal class LazySuspendImpl<T>(
    private val coroutineContext: CoroutineContext,
    block: suspend TestScope.() -> T,
) : LazySuspend<T> {

    private val result = CompletableDeferred<T>()

    init {
        coroutineContext.scheduleSetup {
            try {
                result.complete(block())
            } catch (e: CancellationException) {
                result.cancel(e)
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
    }

    private val scheduledTasksContext = requireNotNull(coroutineContext[ScheduledTasksContext]) { "cannot get value without ScheduledTasksContext present" }
    private val testScheduler = requireNotNull(coroutineContext[ContinuationInterceptor] as? TestDispatcher) { "only works under test dispatcher" }
        .scheduler

    @OptIn(ExperimentalCoroutinesApi::class)
    override val value: T
        get() {
            require(scheduledTasksContext.isScheduled) { "cannot get value without starting the tests first" }
            while (!isCompleted) testScheduler.advanceUntilIdle()
            return result.getCompleted()
        }


    override val isCompleted
        get() = result.isCompleted

    override fun <U> map(transformer: (T) -> U): LazySuspend<U>
        = LazySuspendImpl(coroutineContext) { transformer(value) }
}