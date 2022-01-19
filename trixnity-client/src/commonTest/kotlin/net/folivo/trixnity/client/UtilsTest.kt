package net.folivo.trixnity.client

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.INITIAL_SYNC
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.RUNNING
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class UtilsTest : ShouldSpec({
    timeout = 10_000

    context(StateFlow<SyncApiClient.SyncState>::retryWhenSyncIs.name) {
        should("wait until connected, retry on error") {
            val syncState = MutableStateFlow(SyncApiClient.SyncState.STARTED)

            val onErrorCalled = MutableStateFlow(0)
            val onCancelCalled = MutableStateFlow(0)
            val blockCalled = MutableStateFlow(0)

            val job = launch {
                syncState.retryWhenSyncIs(
                    RUNNING,
                    INITIAL_SYNC,
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
            syncState.value = INITIAL_SYNC
            blockCalled.first { it == 4 } shouldBe 4
            job.cancelAndJoin()
            onErrorCalled.value shouldBe 1
            onCancelCalled.value shouldBe 1
        }
    }
})