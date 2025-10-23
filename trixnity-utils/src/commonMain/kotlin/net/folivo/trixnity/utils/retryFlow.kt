package net.folivo.trixnity.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

data class RetryFlowDelayConfig(
    val scheduleBase: Duration = 100.milliseconds,
    val scheduleFactor: Double = 2.0,
    val scheduleLimit: Duration = 5.minutes,
    val scheduleJitter: ClosedRange<Double> = 0.9..1.1,
) {
    companion object {
        val default: RetryFlowDelayConfig = RetryFlowDelayConfig()
    }
}

private sealed interface RetryFlowResult<T> {
    data class Emit<T>(val value: T) : RetryFlowResult<T>
    data object Suspend : RetryFlowResult<Nothing>
}

fun <T> retryFlow(
    delayConfig: Flow<RetryFlowDelayConfig> = flowOf(RetryFlowDelayConfig()),
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend FlowCollector<T>.() -> Unit,
): Flow<T> = flow {
    var retryCounter = 0
    while (currentCoroutineContext().isActive) {
        val currentDelayConfig = delayConfig.first()
        try {
            emit(RetryFlowResult.Suspend) // ensure, that block is not called before a new value is requested
            emitAll(flow(block).map { RetryFlowResult.Emit(it) })
            retryCounter = 0
        } catch (error: Throwable) {
            if (error is CancellationException) throw error

            val retryDelay =
                (currentDelayConfig.scheduleBase * currentDelayConfig.scheduleFactor.pow(retryCounter))
                    .coerceAtMost(currentDelayConfig.scheduleLimit)

            val jitter =
                if (currentDelayConfig.scheduleJitter.start != currentDelayConfig.scheduleJitter.endInclusive)
                    Random.nextDouble(
                        currentDelayConfig.scheduleJitter.start,
                        currentDelayConfig.scheduleJitter.endInclusive
                    )
                else 1.0

            onError(error, retryDelay)
            coroutineScope {
                select {
                    launch {
                        delay(retryDelay * jitter)
                        retryCounter++
                    }.onJoin {}
                    launch {
                        delayConfig.firstOrNull { currentDelayConfig != it } ?: delay(Duration.INFINITE)
                        retryCounter = 0
                    }.onJoin {}
                }
                currentCoroutineContext().cancelChildren()
            }
        }
    }
}.buffer(0).transform {
    if (it is RetryFlowResult.Emit) emit(it.value)
}

suspend fun retryLoop(
    delayConfig: Flow<RetryFlowDelayConfig> = flowOf(RetryFlowDelayConfig()),
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend () -> Unit,
) = retryFlow<Unit>(
    delayConfig = delayConfig,
    onError = onError,
    block = { block() }
).collect()

fun <T> retryFlow(
    scheduleBase: Duration = RetryFlowDelayConfig.default.scheduleBase,
    scheduleFactor: Double = RetryFlowDelayConfig.default.scheduleFactor,
    scheduleLimit: Duration = RetryFlowDelayConfig.default.scheduleLimit,
    scheduleJitter: ClosedRange<Double> = RetryFlowDelayConfig.default.scheduleJitter,
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend FlowCollector<T>.() -> Unit,
) = retryFlow(
    delayConfig = flowOf(
        RetryFlowDelayConfig(
            scheduleBase = scheduleBase,
            scheduleFactor = scheduleFactor,
            scheduleLimit = scheduleLimit,
            scheduleJitter = scheduleJitter
        )
    ),
    onError = onError,
    block = block,
)

suspend fun retryLoop(
    scheduleBase: Duration = RetryFlowDelayConfig.default.scheduleBase,
    scheduleFactor: Double = RetryFlowDelayConfig.default.scheduleFactor,
    scheduleLimit: Duration = RetryFlowDelayConfig.default.scheduleLimit,
    scheduleJitter: ClosedRange<Double> = RetryFlowDelayConfig.default.scheduleJitter,
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend () -> Unit,
) = retryFlow<Unit>(
    scheduleBase = scheduleBase,
    scheduleFactor = scheduleFactor,
    scheduleLimit = scheduleLimit,
    scheduleJitter = scheduleJitter,
    onError = onError,
    block = { block() },
).collect()

suspend fun <T> retry(
    scheduleBase: Duration = RetryFlowDelayConfig.default.scheduleBase,
    scheduleFactor: Double = RetryFlowDelayConfig.default.scheduleFactor,
    scheduleLimit: Duration = RetryFlowDelayConfig.default.scheduleLimit,
    scheduleJitter: ClosedRange<Double> = RetryFlowDelayConfig.default.scheduleJitter,
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend () -> T,
): T =
    retryFlow(
        scheduleBase = scheduleBase,
        scheduleFactor = scheduleFactor,
        scheduleLimit = scheduleLimit,
        scheduleJitter = scheduleJitter,
        onError = onError,
        block = { emit(block()) },
    ).first()