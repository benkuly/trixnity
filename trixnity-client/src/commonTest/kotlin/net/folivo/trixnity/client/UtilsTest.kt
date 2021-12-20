package net.folivo.trixnity.client

import io.kotest.assertions.retry
import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.sync.SyncApiClient
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({
    timeout = 10_000

    context(StateFlow<SyncApiClient.SyncState>::retryWhenSyncIsRunning.name) {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.STARTED)

            var onErrorCalled = 0
            var onCancelCalled = 0
            var blockCalled = 0

            val job = launch {
                syncState.retryWhenSyncIsRunning(
                    onError = { onErrorCalled++ },
                    onCancel = { onCancelCalled++ },
                    scope = this,
                ) {
                    blockCalled++
                    if (blockCalled == 1) throw RuntimeException("oh no")
                    delay(Duration.INFINITE)
                }
            }

            until(Duration.milliseconds(50), Duration.milliseconds(25).fixed()) {
                job.isActive
            }
            blockCalled shouldBe 0
            syncState.value = SyncApiClient.SyncState.RUNNING
            retry(10, Duration.milliseconds(500), Duration.milliseconds(50)) {
                blockCalled shouldBe 2
            }
            job.cancelAndJoin()
            onErrorCalled shouldBe 1
            onCancelCalled shouldBe 1
        }
    }
})