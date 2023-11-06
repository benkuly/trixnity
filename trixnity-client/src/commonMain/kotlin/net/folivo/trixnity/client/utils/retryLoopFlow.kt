package net.folivo.trixnity.client.utils

import arrow.resilience.Schedule
import arrow.resilience.retry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.api.client.retryOnRateLimit
import net.folivo.trixnity.client.utils.RetryLoopFlowState.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

enum class RetryLoopFlowState {
    RUN, PAUSE, STOP,
}

private interface RetryLoopFlowResult<T> {
    object Suspend : RetryLoopFlowResult<Nothing>
    class Emit<T>(val value: T) : RetryLoopFlowResult<T>
}

suspend fun <T> retryLoopFlow(
    requestedState: Flow<RetryLoopFlowState>,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): Flow<T> = flow {
    coroutineScope {
        val state = MutableSharedFlow<RetryLoopFlowState>(1)
        val stateJob = launch { requestedState.collectLatest { state.emit(it) } }

        val schedule = Schedule.exponential<Throwable>(scheduleBase, scheduleFactor)
//            .or(Schedule.spaced(scheduleLimit)) // works again in a future version of arrow
            .or(Schedule.spaced(scheduleLimit), transform = ::Pair) { a, b -> minOf(a ?: ZERO, b ?: ZERO) }
            .and(Schedule.doWhile { _, _ -> state.first() == RUN })
            .log { input, _ ->
                if (input !is CancellationException) onError(input)
            }

        while (currentCoroutineContext().isActive) {
            try {
                val shouldStop = state.transform {
                    when (it) {
                        RUN -> emit(false)
                        PAUSE -> {} // don't emit and therefore wait for next state
                        STOP -> emit(true)
                    }
                }.first()
                if (shouldStop) break
                emit(
                    RetryLoopFlowResult.Emit(
                        schedule.retry {
                            retryOnRateLimit {
                                block()
                            }
                        }
                    )
                )
                yield()
                emit(RetryLoopFlowResult.Suspend) // if we don't do that, block may be called even if not needed
            } catch (error: Exception) {
                if (error is CancellationException) {
                    onCancel()
                    throw error
                }
            }
        }
        stateJob.cancel()
    }
}.buffer(0)
    .transform {
        if (it is RetryLoopFlowResult.Emit) emit(it.value)
    }

suspend fun retryLoop(
    requestedState: Flow<RetryLoopFlowState>,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> Unit
): Unit = retryLoopFlow(
    requestedState = requestedState,
    scheduleBase = scheduleBase,
    scheduleFactor = scheduleFactor,
    scheduleLimit = scheduleLimit,
    onError = onError,
    onCancel = onCancel,
    block = block
).collect()

suspend fun <T> retryWhen(
    requestedState: Flow<RetryLoopFlowState>,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): T = retryLoopFlow(
    requestedState = requestedState,
    scheduleBase = scheduleBase,
    scheduleFactor = scheduleFactor,
    scheduleLimit = scheduleLimit,
    onError = onError,
    onCancel = onCancel,
    block = block
).first()

suspend fun <T> StateFlow<SyncState>.retryWhenSyncIs(
    syncState: SyncState,
    vararg moreSyncStates: SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): T = coroutineScope {
    val syncStates = listOf(syncState) + moreSyncStates
    retryWhen(
        requestedState = map { if (syncStates.contains(it)) RUN else PAUSE },
        scheduleBase = scheduleBase,
        scheduleFactor = scheduleFactor,
        scheduleLimit = scheduleLimit,
        onError = onError,
        onCancel = onCancel,
        block = block
    )
}

suspend fun StateFlow<SyncState>.retryLoopWhenSyncIs(
    syncState: SyncState,
    vararg moreSyncStates: SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> Unit
) {
    val syncStates = listOf(syncState) + moreSyncStates
    retryLoop(
        requestedState = map { if (syncStates.contains(it)) RUN else PAUSE },
        scheduleBase = scheduleBase,
        scheduleFactor = scheduleFactor,
        scheduleLimit = scheduleLimit,
        onError = onError,
        onCancel = onCancel,
        block = block
    )
}