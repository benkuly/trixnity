package net.folivo.trixnity.client.crypto

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.DeviceKeysStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.MatrixId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({

    context(DeviceKeysStore::waitForUpdateOutdatedKey.name) {
        should("wait until outdated does not contain anything") {
            val outdatedKeys = MutableStateFlow(setOf(MatrixId.UserId("alice", "server")))
            val store = mockk<Store> {
                coEvery { deviceKeys.outdatedKeys } returns outdatedKeys
            }
            val job = launch(Dispatchers.Default) {
                store.deviceKeys.waitForUpdateOutdatedKey()
            }
            until(milliseconds(50), milliseconds(25).fixed()) {
                job.isActive
            }
            outdatedKeys.value = setOf()
            until(milliseconds(50), milliseconds(25).fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
        should("wait until outdated does not contain ids") {
            val outdatedKeys = MutableStateFlow(setOf(MatrixId.UserId("alice", "server")))
            val store = mockk<Store> {
                coEvery { deviceKeys.outdatedKeys } returns outdatedKeys
            }
            val job = launch(Dispatchers.Default) {
                store.deviceKeys.waitForUpdateOutdatedKey(
                    MatrixId.UserId("alice", "server"),
                    MatrixId.UserId("cedric", "server")
                )
            }
            until(milliseconds(50), milliseconds(25).fixed()) {
                job.isActive
            }
            outdatedKeys.value = setOf(MatrixId.UserId("bob", "server"))
            until(milliseconds(50), milliseconds(25).fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
    }
})
