package net.folivo.trixnity.client.crypto

import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.MatrixId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({

    context(Store.DeviceKeysStores::waitForUpdateOutdatedKey.name) {
        should("wait until outdated does not contain anything") {
            val store = InMemoryStore()
            store.deviceKeys.outdatedKeys.value = setOf(MatrixId.UserId("alice", "server"))
            val job = launch {
                store.deviceKeys.waitForUpdateOutdatedKey()
            }
            until(milliseconds(50)) {
                job.isActive
            }
            store.deviceKeys.outdatedKeys.value = setOf()
            job.join()
            job.isActive shouldBe false
        }
        should("wait until outdated does not contain ids") {
            val store = InMemoryStore()
            store.deviceKeys.outdatedKeys.value = setOf(MatrixId.UserId("alice", "server"))
            val job = launch {
                store.deviceKeys.waitForUpdateOutdatedKey(
                    MatrixId.UserId("alice", "server"),
                    MatrixId.UserId("cedric", "server")
                )
            }
            until(milliseconds(50)) {
                job.isActive
            }
            store.deviceKeys.outdatedKeys.value = setOf(MatrixId.UserId("bob", "server"))
            job.join()
            job.isActive shouldBe false
        }
    }
})
