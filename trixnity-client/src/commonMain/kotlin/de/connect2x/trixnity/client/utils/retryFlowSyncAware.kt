package de.connect2x.trixnity.client.utils

import kotlinx.coroutines.flow.*
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.utils.RetryFlowDelayConfig
import de.connect2x.trixnity.utils.retryFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun <T> StateFlow<SyncState>.retryFlow(
    delayConfigWhenSyncRunning: RetryFlowDelayConfig = RetryFlowDelayConfig().copy(scheduleLimit = 10.seconds),
    delayConfigWhenSyncNotRunning: RetryFlowDelayConfig = RetryFlowDelayConfig(),
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend FlowCollector<T>.() -> Unit,
): Flow<T> =
    retryFlow(
        delayConfig = map { syncState ->
            when (syncState) {
                SyncState.INITIAL_SYNC,
                SyncState.STARTED,
                SyncState.RUNNING -> delayConfigWhenSyncRunning

                SyncState.ERROR,
                SyncState.TIMEOUT,
                SyncState.STOPPED -> delayConfigWhenSyncNotRunning
            }
        },
        onError = onError,
        block = block
    )

suspend fun StateFlow<SyncState>.retryLoop(
    delayConfigWhenSyncRunning: RetryFlowDelayConfig = RetryFlowDelayConfig().copy(scheduleLimit = 10.seconds),
    delayConfigWhenSyncNotRunning: RetryFlowDelayConfig = RetryFlowDelayConfig(),
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend () -> Unit,
) = retryFlow<Unit>(
    delayConfigWhenSyncRunning = delayConfigWhenSyncRunning,
    delayConfigWhenSyncNotRunning = delayConfigWhenSyncNotRunning,
    onError = onError,
    block = { block() }
).collect()

suspend fun <T> StateFlow<SyncState>.retry(
    delayConfigWhenSyncRunning: RetryFlowDelayConfig = RetryFlowDelayConfig().copy(scheduleLimit = 10.seconds),
    delayConfigWhenSyncNotRunning: RetryFlowDelayConfig = RetryFlowDelayConfig(),
    onError: suspend (error: Throwable, delay: Duration) -> Unit = { _, _ -> },
    block: suspend () -> T,
): T = retryFlow(
    delayConfigWhenSyncRunning = delayConfigWhenSyncRunning,
    delayConfigWhenSyncNotRunning = delayConfigWhenSyncNotRunning,
    onError = onError,
    block = { emit(block()) },
).first()