package net.folivo.trixnity.client.utils

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.utils.RetryLoopFlowState.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RetryLoopFlowTest : ShouldSpec({
    timeout = 10_000

    context("retryLoopFlow") {
        context("requested state") {
            should("RUN") {
                val requestedState = MutableStateFlow(RUN)
                val blockCalled = MutableStateFlow(0)
                val onErrorCalled = MutableStateFlow(0)
                val onCancelCalled = MutableStateFlow(0)
                val result = mutableListOf<String>()

                val job = launch {
                    retryLoopFlow(
                        requestedState = requestedState,
                        scheduleBase = 10.milliseconds,
                        scheduleLimit = 200.milliseconds,
                        onError = { onErrorCalled.update { it + 1 } },
                        onCancel = { onCancelCalled.update { it + 1 } },
                    ) {
                        when (blockCalled.updateAndGet { it + 1 }) {
                            1 -> throw RuntimeException("oh no")
                            2, 3 -> {}
                            else -> delay(Duration.INFINITE)
                        }
                        "dino"
                    }.collect {
                        result.add(it)
                    }
                }

                blockCalled.first { it == 4 }
                onErrorCalled.value shouldBe 1
                onCancelCalled.value shouldBe 0
                result shouldHaveSize 2
                job.cancel()
                onCancelCalled.first { it == 1 }
            }
            should("PAUSE") {
                val requestedState = MutableStateFlow(PAUSE)
                val blockCalled = MutableStateFlow(0)
                val onErrorCalled = MutableStateFlow(0)
                val onCancelCalled = MutableStateFlow(0)
                val result = mutableListOf<String>()

                val job = launch {
                    retryLoopFlow(
                        requestedState = requestedState,
                        scheduleBase = 10.milliseconds,
                        scheduleLimit = 200.milliseconds,
                        onError = { onErrorCalled.update { it + 1 } },
                        onCancel = { onCancelCalled.update { it + 1 } },
                    ) {
                        when (blockCalled.updateAndGet { it + 1 }) {
                            1 -> throw RuntimeException("oh no")
                            2, 3 -> {}
                            else -> delay(Duration.INFINITE)
                        }
                        "dino"
                    }.collect {
                        result.add(it)
                    }
                }

                delay(100.milliseconds)
                blockCalled.value shouldBe 0
                onErrorCalled.value shouldBe 0
                onCancelCalled.value shouldBe 0
                result shouldHaveSize 0
                job.cancel()
                onCancelCalled.first { it == 1 }
            }
            should("RUN after PAUSE") {
                val requestedState = MutableStateFlow(RUN)
                val blockCalled = MutableStateFlow(0)

                val blockContinue = MutableSharedFlow<Unit>()

                val job = launch {
                    retryLoopFlow(
                        requestedState = requestedState,
                        scheduleBase = 10.milliseconds,
                        scheduleLimit = 200.milliseconds,
                    ) {
                        blockCalled.update { it + 1 }
                        blockContinue.first()
                        "dino"
                    }.collect()
                }

                blockCalled.first { it == 1 }
                requestedState.value = PAUSE
                blockContinue.emit(Unit)

                delay(100.milliseconds)

                blockCalled.value shouldBe 1

                requestedState.value = RUN
                blockCalled.first { it == 2 }
                blockContinue.emit(Unit)

                blockCalled.first { it == 3 }
                job.cancel()
            }
        }
        should("STOP") {
            val requestedState = MutableStateFlow(STOP)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                retryLoopFlow(
                    requestedState = requestedState,
                    scheduleBase = 10.milliseconds,
                    scheduleLimit = 200.milliseconds,
                ) {
                    blockCalled.update { it + 1 }
                    "dino"
                }.collect()
            }

            delay(100.milliseconds)

            blockCalled.value shouldBe 0

            job.isActive shouldBe false
        }
    }
    context(StateFlow<SyncState>::retryLoopWhenSyncIs.name) {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                syncState.retryLoopWhenSyncIs(
                    SyncState.RUNNING,
                    SyncState.INITIAL_SYNC,
                    onError = { onErrorCalled.update { it + 1 } },
                    onCancel = { onCancelCalled.update { it + 1 } },
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
            syncState.value = SyncState.RUNNING
            blockCalled.first { it == 2 } shouldBe 2
            syncState.value = SyncState.INITIAL_SYNC
            blockCalled.first { it == 4 } shouldBe 4
            job.cancelAndJoin()
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 1
        }
    }
    context("retryWhenSyncIs") {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val result = async {
                syncState.retryWhenSyncIs(
                    SyncState.RUNNING,
                    onError = { onErrorCalled.update { it + 1 } },
                    onCancel = { onCancelCalled.update { it + 1 } },
                ) {
                    when (blockCalled.updateAndGet { it + 1 }) {
                        1 -> throw RuntimeException("oh no")
                        else -> "hi"
                    }
                }
            }

            until(50.milliseconds, 25.milliseconds.fixed()) {
                result.isActive
            }
            blockCalled.value shouldBe 0
            syncState.value = SyncState.RUNNING
            blockCalled.first { it == 2 } shouldBe 2
            result.await() shouldBe "hi"
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 1
        }
    }
})