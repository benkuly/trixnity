package net.folivo.trixnity.crypto.driver.test

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

open class GroupSessionTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    private val someKey = driver.key.pickleKey(Random.nextBytes(32))
    private val emptyKey = driver.key.pickleKey()

    @Test
    fun createOutboundSession() = runTest {
        driver.megolm.groupSession().use { outboundSession ->
            outboundSession.sessionId shouldNot beBlank()
            outboundSession.sessionKey.base64 shouldNot beBlank()
            outboundSession.messageIndex shouldBe 0
        }
    }

    @Test
    fun encryptMessage() = runTest {
        driver.megolm.groupSession().use { outboundSession ->
            outboundSession.encrypt("I'm clear!").base64 shouldNot beBlank()
            outboundSession.messageIndex shouldBe 1
        }
    }

    @Test
    fun create_shouldHaveUniqueIdAndKey() = runTest {
        // This test validates random series are provide enough random values.
        val size = 10
        val sessions = flow { while (true) emit(driver.megolm.groupSession()) }.take(size).toList()
        assertSoftly {
            sessions.map { it.sessionId }.toSet() shouldHaveSize size
            sessions.map { it.sessionKey }.toSet() shouldHaveSize size
        }
        sessions.forEach { it.close() }
    }

    @Test
    fun pickle() = runTest {
        driver.megolm.groupSession().use { session ->
            session.pickle(someKey) shouldNot beBlank()
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        driver.megolm.groupSession().use { session ->
            session.pickle(emptyKey) shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        val pickle = driver.megolm.groupSession().use { session ->
            session.pickle(someKey) to session.sessionId
        }
        driver.megolm.groupSession.fromPickle(pickle.first, someKey).use { session ->
            session.sessionId shouldBe pickle.second
        }
    }

    @Test
    fun unpickleWIthEmptyKey() = runTest {
        val pickle = driver.megolm.groupSession().use { session ->
            session.pickle(emptyKey) to session.sessionId
        }
        driver.megolm.groupSession.fromPickle(pickle.first, emptyKey).use { session ->
            session.sessionId shouldBe pickle.second
        }
    }
}