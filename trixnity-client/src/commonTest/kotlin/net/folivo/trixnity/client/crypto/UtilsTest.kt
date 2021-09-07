package net.folivo.trixnity.client.crypto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.MatrixId

class UtilsTest : ShouldSpec({

    context(Store.DeviceKeysStores::waitForUpdateOutdatedKey.name) {
        should("wait until outdated does not contain anything") {
            val store = InMemoryStore()
            store.deviceKeys.outdatedKeys.value = setOf(MatrixId.UserId("alice", "server"))
            val job = launch {
                store.deviceKeys.waitForUpdateOutdatedKey()
            }
            delay(50)
            job.isActive shouldBe true
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
            delay(50)
            job.isActive shouldBe true
            store.deviceKeys.outdatedKeys.value = setOf(MatrixId.UserId("bob", "server"))
            job.join()
            job.isActive shouldBe false
        }
    }
})
