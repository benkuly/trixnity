package net.folivo.trixnity.client

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.sync.SyncApiClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class UtilsTest : ShouldSpec({
    timeout = 10_000

    context(StateFlow<SyncApiClient.SyncState>::retryWhenSyncIsRunning.name) {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                syncState.retryWhenSyncIsRunning(
                    onError = { onErrorCalled.update { it + 1 } },
                    onCancel = { onCancelCalled.update { it + 1 } },
                    scope = this,
                ) {
                    if (blockCalled.updateAndGet { it + 1 } == 1) throw RuntimeException("oh no")
                    delay(Duration.INFINITE)
                }
            }

            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            blockCalled.value shouldBe 0
            syncState.value = SyncApiClient.SyncState.RUNNING
            blockCalled.first { it == 2 } shouldBe 2
            job.cancelAndJoin()
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 1
        }
    }
})