package net.folivo.trixnity.olm

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmOutboundGroupSessionTest {

    @Test
    fun createOutboundSession() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            outboundSession.sessionId shouldNot beBlank()
            outboundSession.sessionKey shouldNot beBlank()
            outboundSession.messageIndex shouldBe 0
        }
    }

    @Test
    fun encryptMessage() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            outboundSession.encrypt("I'm clear!") shouldNot beBlank()
            outboundSession.messageIndex shouldBe 1
        }
    }

    @Test
    fun create_shouldHaveUniqueIdAndKey() = runTest {
        // This test validates random series are provide enough random values.
        val size = 10
        val sessions = flow { while (true) emit(OlmOutboundGroupSession.create()) }.take(size).toList()
        assertSoftly {
            sessions.map { it.sessionId }.toSet() shouldHaveSize size
            sessions.map { it.sessionKey }.toSet() shouldHaveSize size
        }
        sessions.forEach { it.free() }
    }

    @Test
    fun pickle() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("someKey") shouldNot beBlank()
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("") shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        val pickle = freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("someKey") to session.sessionId
        }
        freeAfter(OlmOutboundGroupSession.unpickle("someKey", pickle.first)) { session ->
            session.sessionId shouldBe pickle.second
        }
    }

    @Test
    fun unpickleWIthEmptyKey() = runTest {
        val pickle = freeAfter(OlmOutboundGroupSession.create()) { session ->
            session.pickle("") to session.sessionId
        }
        freeAfter(OlmOutboundGroupSession.unpickle("", pickle.first)) { session ->
            session.sessionId shouldBe pickle.second
        }
    }
}