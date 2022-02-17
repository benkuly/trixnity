package net.folivo.trixnity.client

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.ErrorResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({
    timeout = 10_000

    context(StateFlow<SyncApiClient.SyncState>::retryInfiniteWhenSyncIs.name) {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                syncState.retryInfiniteWhenSyncIs(
                    RUNNING,
                    SyncApiClient.SyncState.INITIAL_SYNC,
                    onError = { onErrorCalled.update { it + 1 } },
                    onCancel = { onCancelCalled.update { it + 1 } },
                    scope = this,
                ) {
                    when (blockCalled.updateAndGet { it + 1 }) {
                        1 -> throw RuntimeException("oh no")
                        2, 3 -> delay(5)
                        else -> delay(Duration.INFINITE)
                    }
                }
            }

            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            blockCalled.value shouldBe 0
            syncState.value = RUNNING
            blockCalled.first { it == 2 } shouldBe 2
            syncState.value = SyncApiClient.SyncState.INITIAL_SYNC
            blockCalled.first { it == 4 } shouldBe 4
            job.cancelAndJoin()
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 1
        }
        should("retry on rate limit") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.RUNNING)

            val blockCalled = MutableStateFlow(0)

            measureTime {
                val job = launch {
                    syncState.retryInfiniteWhenSyncIs(
                        RUNNING,
                        scheduleBase = 10.milliseconds,
                        scope = this,
                    ) {
                        when (blockCalled.updateAndGet { it + 1 }) {
                            1 -> throw MatrixServerException(
                                HttpStatusCode.TooManyRequests,
                                ErrorResponse.LimitExceeded("", retryAfterMillis = 500)
                            )
                            else -> delay(Duration.INFINITE)
                        }
                    }
                }
                blockCalled.first { it == 2 } shouldBe 2
                job.cancelAndJoin()
            } shouldBeGreaterThan 500.milliseconds
        }
    }
    context("retryWhenSyncIs") {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                syncState.retryWhenSyncIs(
                    RUNNING,
                    onError = { onErrorCalled.update { it + 1 } },
                    onCancel = { onCancelCalled.update { it + 1 } },
                    scope = this,
                ) {
                    when (blockCalled.updateAndGet { it + 1 }) {
                        1 -> throw RuntimeException("oh no")
                        else -> "hi"
                    }
                } shouldBe "hi"
            }

            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            blockCalled.value shouldBe 0
            syncState.value = RUNNING
            blockCalled.first { it == 2 } shouldBe 2
            job.cancelAndJoin()
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 0
        }
        should("retry on rate limit") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.RUNNING)

            val blockCalled = MutableStateFlow(0)

            measureTime {
                val job = launch {
                    syncState.retryWhenSyncIs(
                        RUNNING,
                        scheduleBase = 10.milliseconds,
                        scope = this,
                    ) {
                        when (blockCalled.updateAndGet { it + 1 }) {
                            1 -> throw MatrixServerException(
                                HttpStatusCode.TooManyRequests,
                                ErrorResponse.LimitExceeded("", retryAfterMillis = 500)
                            )
                            else -> "hi"
                        }
                    } shouldBe "hi"
                }
                blockCalled.first { it == 2 } shouldBe 2
                job.cancelAndJoin()
            } shouldBeGreaterThan 500.milliseconds
        }
    }
})