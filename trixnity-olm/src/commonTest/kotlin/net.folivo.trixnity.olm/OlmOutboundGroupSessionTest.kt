package net.folivo.trixnity.olm

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlin.test.Test


class OlmOutboundGroupSessionTest {

    @Test
    fun createOutboundSession() = initTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            outboundSession.sessionId shouldNot beBlank()
            outboundSession.sessionKey shouldNot beBlank()
            outboundSession.messageIndex shouldBe 0
        }
    }

    @Test
    fun encryptMessage() = initTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            outboundSession.encrypt("I'm clear!") shouldNot beBlank()
            outboundSession.messageIndex shouldBe 1
        }
    }

    @Test
    fun create_shouldHaveUniqueIdAndKey() = initTest {
        // This test validates random series are provide enough random values.
        val size = 10
        val sessions = generateSequence { OlmOutboundGroupSession.create() }.take(size).toList()
        assertSoftly {
            sessions.map { it.sessionId }.toSet() shouldHaveSize size
            sessions.map { it.sessionKey }.toSet() shouldHaveSize size
        }
        sessions.forEach { it.free() }
    }

    @Test
    fun pickle() = initTest {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("someKey") shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = initTest {
        val pickle = freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("someKey") to session.sessionId
        }
        freeAfter(OlmOutboundGroupSession.unpickle("someKey", pickle.first)) { session ->
            session.sessionId shouldBe pickle.second
        }
    }
}