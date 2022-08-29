package net.folivo.trixnity.client.key

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.*
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.milliseconds

class UtilsTest : ShouldSpec({
    timeout = 60_000

    lateinit var keyStore: KeyStore
    lateinit var storeScope: CoroutineScope

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(storeScope)
    }

    afterTest {
        storeScope.cancel()
    }

    context("waitForUpdateOutdatedKey") {
        should("wait until outdated does not contain anything") {
            keyStore.outdatedKeys.value = setOf(UserId("alice", "server"))
            val job = launch(Dispatchers.Default) {
                keyStore.waitForUpdateOutdatedKey()
            }
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                job.isActive
            }
            keyStore.outdatedKeys.value = setOf()
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
        should("wait until outdated does not contain ids") {
            keyStore.outdatedKeys.value = setOf(UserId("alice", "server"))
            val job = launch(Dispatchers.Default) {
                keyStore.waitForUpdateOutdatedKey(
                    setOf(
                        UserId("alice", "server"),
                        UserId("cedric", "server")
                    )
                )
            }
            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            keyStore.outdatedKeys.value = setOf(UserId("bob", "server"))
            until(50.milliseconds, 25.milliseconds.fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
    }
})
