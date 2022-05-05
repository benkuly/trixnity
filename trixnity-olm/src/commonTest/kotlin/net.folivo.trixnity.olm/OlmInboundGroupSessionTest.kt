package net.folivo.trixnity.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmInboundGroupSessionTest {

    private val sessionKey = "AgAAAAAwMTIzNDU2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMzQ1Njc4OUFCREVGM" +
            "DEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkRFRjAxMjM0NTY3ODlBQkNERUYwMTIzND" +
            "U2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMw0bdg1BDq4Px/slBow06q8n/B9WBfw" +
            "WYyNOB8DlUmXGGwrFmaSb9bR/eY8xgERrxmP07hFmD9uqA2p8PMHdnV5ysmgufE6oLZ5+" +
            "8/mWQOW3VVTnDIlnwd8oHUYRuk8TCQ"

    @Test
    fun createInboundSession() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { aliceOutboundSession ->
            freeAfter(OlmInboundGroupSession.create(aliceOutboundSession.sessionKey)) { bobInboundSession ->
                assertSoftly(bobInboundSession.sessionId) {
                    shouldNotBeBlank()
                    shouldBe(aliceOutboundSession.sessionId)
                }
            }
        }
    }

    @Test
    fun decrypt() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { aliceOutboundSession ->
            freeAfter(OlmInboundGroupSession.create(aliceOutboundSession.sessionKey)) { bobInboundSession ->
                val encryptedMessage = aliceOutboundSession.encrypt("Hello from Alice!")
                assertSoftly(bobInboundSession.decrypt(encryptedMessage)) {
                    index shouldBe 0
                    message shouldBe "Hello from Alice!"
                }
            }
        }
    }

    @Test
    fun decrypt_withEmoji() = runTest {
        val sessionKey =
            "AgAAAAycZE6AekIctJWYxd2AWLOY15YmxZODm/WkgbpWkyycp6ytSp/R+wo84jRrzBNWmv6ySLTZ9R0EDOk9VI2eZyQ6Efdwyo1mAvrWvTkZl9yALPdkOIVHywyG65f1SNiLrnsln3hgsT1vUrISGyKtsljoUgQpr3JDPEhD0ilAi63QBjhnGCW252b+7nF+43rb6O6lwm93LaVwe2341Gdp6EkhTUvetALezEqDOtKN00wVqAbq0RQAnUJIowxHbMswg+FyoR1K1oCjnVEoF23O9xlAn5g1XtuBZP3moJlR2lwsBA"
        val msgWithEmoji =
            "AwgNEpABpjs+tYF+0y8bWtzAgYAC3N55p5cPJEEiGPU1kxIHSY7f2aG5Fj4wmcsXUkhDv0UePj922kgf+Q4dFsPHKq2aVA93n8DJAQ/FRfcM98B9E6sKCZ/PsCF78uBvF12Aaq9D3pUHBopdd7llUfVq29d5y6ZwX5VDoqV2utsATkKjXYV9CbfZuvvBMQ30ZLjEtyUUBJDY9K4FxEFcULytA/IkVnATTG9ERuLF/yB6ukSFR+iUWRYAmtuOuU0k9BvaqezbGqNoK5Grlkes+dYX6/0yUObumcw9/iAI"

        freeAfter(OlmInboundGroupSession.create(sessionKey)) { inboundSession ->
            val decryptedMessage = inboundSession.decrypt(msgWithEmoji)
            assertSoftly(decryptedMessage) {
                index shouldBe 13
                message shouldNot beBlank()
            }
        }
    }

    @Test
    fun decrypt_withInvalidBase64() = runTest {
        val sessionKey =
            "AgAAAAycZE6AekIctJWYxd2AWLOY15YmxZODm/WkgbpWkyycp6ytSp/R+wo84jRrzBNWmv6ySLTZ9R0EDOk9VI2eZyQ6Efdwyo1mAvrWvTkZl9yALPdkOIVHywyG65f1SNiLrnsln3hgsT1vUrISGyKtsljoUgQpr3JDPEhD0ilAi63QBjhnGCW252b+7nF+43rb6O6lwm93LaVwe2341Gdp6EkhTUvetALezEqDOtKN00wVqAbq0RQAnUJIowxHbMswg+FyoR1K1oCjnVEoF23O9xlAn5g1XtuBZP3moJlR2lwsBA"
        val msgWithInvalidBase64 = "AwgANYTHINGf87ge45ge7gr*/rg5ganything4gr41rrgr4re55tanythingmcsXUkhDv0UePj922kgf+"

        freeAfter(OlmInboundGroupSession.create(sessionKey)) { inboundSession ->
            shouldThrow<OlmLibraryException> {
                inboundSession.decrypt(msgWithInvalidBase64)
            }.message shouldBe "INVALID_BASE64"
        }
    }


    @Test
    fun importExport() = runTest {
        val encryptedMessage = "AwgAEhAcbh6UpbByoyZxufQ+h2B+8XHMjhR69G8F4+qjMaFlnIXusJZX3r8LnRORG9T3D" +
                "XFdbVuvIWrLyRfm4i8QRbe8VPwGRFG57B1CtmxanuP8bHtnnYqlwPsD"
        val export = freeAfter(OlmInboundGroupSession.create(sessionKey)) { inboundSession ->
            assertSoftly(inboundSession.decrypt(encryptedMessage)) {
                message shouldBe "Message"
                index shouldBe 0
            }
            val firstKnownIndex = inboundSession.firstKnownIndex
            firstKnownIndex shouldBeGreaterThanOrEqual 0
            inboundSession.export(inboundSession.firstKnownIndex)
        }
        freeAfter(OlmInboundGroupSession.import(export)) { inboundSession ->
            assertSoftly(inboundSession.decrypt(encryptedMessage)) {
                message shouldBe "Message"
                index shouldBe 0
            }
            val firstKnownIndex = inboundSession.firstKnownIndex
            firstKnownIndex shouldBeGreaterThanOrEqual 0
        }
    }


    @Test
    fun pickle() = runTest {
        freeAfter(OlmInboundGroupSession.create(sessionKey)) { session ->
            session.pickle("someKey") shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        val pickle = freeAfter(OlmInboundGroupSession.create(sessionKey)) { session ->
            session.pickle("someKey") to session.sessionId
        }
        freeAfter(OlmInboundGroupSession.unpickle("someKey", pickle.first)) { session ->
            session.sessionId shouldBe pickle.second
        }
    }
}