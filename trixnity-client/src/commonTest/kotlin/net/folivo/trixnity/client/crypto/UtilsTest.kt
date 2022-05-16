package net.folivo.trixnity.client.crypto

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.*
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.milliseconds

class UtilsTest : ShouldSpec({

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
    }

    afterTest {
        storeScope.cancel()
    }

    context("waitForUpdateOutdatedKey") {
        should("wait until outdated does not contain anything") {
            store.keys.outdatedKeys.value = setOf(UserId("alice", "server"))
            val job = launch(Dispatchers.Default) {
                store.keys.waitForUpdateOutdatedKey()
            }
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                job.isActive
            }
            store.keys.outdatedKeys.value = setOf()
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
        should("wait until outdated does not contain ids") {
            store.keys.outdatedKeys.value = setOf(UserId("alice", "server"))
            val job = launch(Dispatchers.Default) {
                store.keys.waitForUpdateOutdatedKey(
                    setOf(
                        UserId("alice", "server"),
                        UserId("cedric", "server")
                    )
                )
            }
            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            store.keys.outdatedKeys.value = setOf(UserId("bob", "server"))
            until(50.milliseconds, 25.milliseconds.fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
    }
})
