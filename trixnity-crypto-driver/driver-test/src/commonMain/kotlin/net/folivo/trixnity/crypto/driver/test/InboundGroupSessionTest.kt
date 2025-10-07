package net.folivo.trixnity.crypto.driver.test

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotBeBlank
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

open class InboundGroupSessionTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    private val sessionKey = driver.megolm.sessionKey(
        "AgAAAAAwMTIzNDU2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMzQ1Njc4OUFCREVGM" + "DEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkRFRjAxMjM0NTY3ODlBQkNERUYwMTIzND" + "U2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMw0bdg1BDq4Px/slBow06q8n/B9WBfw" + "WYyNOB8DlUmXGGwrFmaSb9bR/eY8xgERrxmP07hFmD9uqA2p8PMHdnV5ysmgufE6oLZ5+" + "8/mWQOW3VVTnDIlnwd8oHUYRuk8TCQ"
    )

    private val someKey = driver.key.pickleKey(Random.nextBytes(32))
    private val emptyKey = driver.key.pickleKey()

    @Test
    fun createInboundSession() = runTest {
        driver.megolm.groupSession().use { aliceOutboundSession ->
            driver.megolm.inboundGroupSession(aliceOutboundSession.sessionKey).use { bobInboundSession ->
                assertSoftly(bobInboundSession.sessionId) {
                    shouldNotBeBlank()
                    shouldBe(aliceOutboundSession.sessionId)
                }
            }
        }
    }

    @Test
    fun decrypt() = runTest {
        driver.megolm.groupSession().use { aliceOutboundSession ->
            driver.megolm.inboundGroupSession(aliceOutboundSession.sessionKey).use { bobInboundSession ->
                val encryptedMessage = aliceOutboundSession.encrypt("Hello from Alice!")
                assertSoftly(bobInboundSession.decrypt(encryptedMessage)) {
                    messageIndex shouldBe 0
                    plaintext shouldBe "Hello from Alice!"
                }
            }
        }
    }

    @Test
    fun decrypt_withEmoji() = runTest {
        val sessionKey =
            driver.megolm.sessionKey("AgAAAAycZE6AekIctJWYxd2AWLOY15YmxZODm/WkgbpWkyycp6ytSp/R+wo84jRrzBNWmv6ySLTZ9R0EDOk9VI2eZyQ6Efdwyo1mAvrWvTkZl9yALPdkOIVHywyG65f1SNiLrnsln3hgsT1vUrISGyKtsljoUgQpr3JDPEhD0ilAi63QBjhnGCW252b+7nF+43rb6O6lwm93LaVwe2341Gdp6EkhTUvetALezEqDOtKN00wVqAbq0RQAnUJIowxHbMswg+FyoR1K1oCjnVEoF23O9xlAn5g1XtuBZP3moJlR2lwsBA")
        val msgWithEmoji =
            driver.megolm.message("AwgNEpABpjs+tYF+0y8bWtzAgYAC3N55p5cPJEEiGPU1kxIHSY7f2aG5Fj4wmcsXUkhDv0UePj922kgf+Q4dFsPHKq2aVA93n8DJAQ/FRfcM98B9E6sKCZ/PsCF78uBvF12Aaq9D3pUHBopdd7llUfVq29d5y6ZwX5VDoqV2utsATkKjXYV9CbfZuvvBMQ30ZLjEtyUUBJDY9K4FxEFcULytA/IkVnATTG9ERuLF/yB6ukSFR+iUWRYAmtuOuU0k9BvaqezbGqNoK5Grlkes+dYX6/0yUObumcw9/iAI")

        driver.megolm.inboundGroupSession(sessionKey).use { inboundSession ->
            val decryptedMessage = inboundSession.decrypt(msgWithEmoji)
            assertSoftly(decryptedMessage) {
                messageIndex shouldBe 13
                plaintext shouldNot beBlank()
            }
        }
    }

    @Test
    fun decrypt_exceptionOnUnknownMessageKey() = runTest {
        driver.megolm.groupSession().use { aliceOutboundSession ->
            val encryptedMessage = aliceOutboundSession.encrypt("Hello from Alice!")
            driver.megolm.inboundGroupSession(aliceOutboundSession.sessionKey).use { bobInboundSession ->
                shouldThrow<CryptoDriverException> { bobInboundSession.decrypt(encryptedMessage) }
            }
        }
    }


    @Test
    fun importExport() = runTest {
        val encryptedMessage = driver.megolm.message(
            "AwgAEhAcbh6UpbByoyZxufQ+h2B+8XHMjhR69G8F4+qjMaFlnIXusJZX3r8LnRORG9T3D" + "XFdbVuvIWrLyRfm4i8QRbe8VPwGRFG57B1CtmxanuP8bHtnnYqlwPsD"
        )
        val export = driver.megolm.inboundGroupSession(sessionKey).use { inboundSession ->
            assertSoftly(inboundSession.decrypt(encryptedMessage)) {
                plaintext shouldBe "Message"
                messageIndex shouldBe 0
            }
            val firstKnownIndex = inboundSession.firstKnownIndex
            firstKnownIndex shouldBeGreaterThanOrEqual 0
            inboundSession.exportAtFirstKnownIndex()
        }
        driver.megolm.inboundGroupSession.import(export).use { inboundSession ->
            assertSoftly(inboundSession.decrypt(encryptedMessage)) {
                plaintext shouldBe "Message"
                messageIndex shouldBe 0
            }
            val firstKnownIndex = inboundSession.firstKnownIndex
            firstKnownIndex shouldBeGreaterThanOrEqual 0
        }
    }


    @Test
    fun pickle() = runTest {
        driver.megolm.inboundGroupSession(sessionKey).use { session ->
            session.pickle(someKey) shouldNot beBlank()
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        driver.megolm.inboundGroupSession(sessionKey).use { session ->
            session.pickle(emptyKey) shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        val pickle = driver.megolm.inboundGroupSession(sessionKey).use { session ->
            session.pickle(someKey) to session.sessionId
        }
        driver.megolm.inboundGroupSession.fromPickle(pickle.first, someKey).use { session ->
            session.sessionId shouldBe pickle.second
        }
    }

    @Test
    fun unpickleWithEmptyKey() = runTest {
        val pickle = driver.megolm.inboundGroupSession(sessionKey).use { session ->
            session.pickle(emptyKey) to session.sessionId
        }
        driver.megolm.inboundGroupSession.fromPickle(pickle.first, emptyKey).use { session ->
            session.sessionId shouldBe pickle.second
        }
    }
}