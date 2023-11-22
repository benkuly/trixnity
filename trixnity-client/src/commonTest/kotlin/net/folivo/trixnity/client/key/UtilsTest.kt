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
        should("wait until outdated does not contain ids") {
            keyStore.updateOutdatedKeys { setOf(UserId("alice", "server")) }
            val job = launch(Dispatchers.Default) {
                keyStore.waitForUpdateOutdatedKey(
                    UserId("alice", "server"),
                )
            }
            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            keyStore.updateOutdatedKeys { setOf(UserId("bob", "server")) }
            until(50.milliseconds, 25.milliseconds.fixed()) {
                !job.isActive
            }
            job.cancelAndJoin()
        }
    }
})
