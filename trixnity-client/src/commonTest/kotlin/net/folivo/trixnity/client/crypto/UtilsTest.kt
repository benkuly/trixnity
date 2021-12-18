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
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({

    context(KeyStore::waitForUpdateOutdatedKey.name) {
        should("wait until outdated does not contain anything") {
            val outdatedKeys = MutableStateFlow(setOf(UserId("alice", "server")))
            val store = mockk<Store> {
                coEvery { keys.outdatedKeys } returns outdatedKeys
            }
            val job = launch(Dispatchers.Default) {
                store.keys.waitForUpdateOutdatedKey()
            }
            until(milliseconds(1_000), milliseconds(50).fixed()) {
                job.isActive
            }
            outdatedKeys.value = setOf()
            until(milliseconds(1_000), milliseconds(50).fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
        should("wait until outdated does not contain ids") {
            val outdatedKeys = MutableStateFlow(setOf(UserId("alice", "server")))
            val store = mockk<Store> {
                coEvery { keys.outdatedKeys } returns outdatedKeys
            }
            val job = launch(Dispatchers.Default) {
                store.keys.waitForUpdateOutdatedKey(
                    UserId("alice", "server"),
                    UserId("cedric", "server")
                )
            }
            until(milliseconds(50), milliseconds(25).fixed()) {
                job.isActive
            }
            outdatedKeys.value = setOf(UserId("bob", "server"))
            until(milliseconds(50), milliseconds(25).fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
    }
})
