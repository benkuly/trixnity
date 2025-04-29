package net.folivo.trixnity.client.store

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.eventually
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class OlmStoreTest : TrixnityBaseTest() {

    private val olmAccountRepository = InMemoryOlmAccountRepository() as OlmAccountRepository
    private val inboundMegolmSessionRepository =
        InMemoryInboundMegolmSessionRepository() as InboundMegolmSessionRepository

    private val cut = OlmCryptoStore(
        olmAccountRepository,
        InMemoryOlmForgetFallbackKeyAfterRepository(),
        InMemoryOlmSessionRepository(),
        inboundMegolmSessionRepository,
        InMemoryInboundMegolmMessageIndexRepository(),
        InMemoryOutboundMegolmSessionRepository(),
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    private val session = StoredInboundMegolmSession(
        senderKey = Curve25519KeyValue("senderCurve"),
        sessionId = "session",
        roomId = RoomId("room", "server"),
        firstKnownIndex = 24,
        hasBeenBackedUp = false,
        isTrusted = true,
        senderSigningKey = Ed25519KeyValue("edKey"),
        forwardingCurve25519KeyChain = listOf(),
        pickled = "pickle"
    )

    @Test
    fun `init » load values from database`() = runTest {
        olmAccountRepository.save(1, "olm_account")

        cut.init(this)

        cut.updateOlmAccount { "olm_account" }
    }

    @Test
    fun `init » start job which saves changes to database and fills notBackedUp inbound megolm sessions`() =
        runTest {
            inboundMegolmSessionRepository.save(
                InboundMegolmSessionRepositoryKey(
                    sessionId = "session1",
                    roomId = RoomId("room", "server"),
                ), StoredInboundMegolmSession(
                    senderKey = Curve25519KeyValue("senderCurve1"),
                    senderSigningKey = Ed25519KeyValue("senderEd1"),
                    sessionId = "session1",
                    roomId = RoomId("room", "server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled1"
                )
            )
            inboundMegolmSessionRepository.save(
                InboundMegolmSessionRepositoryKey(
                    sessionId = "session2",
                    roomId = RoomId("room", "server"),
                ), StoredInboundMegolmSession(
                    senderKey = Curve25519KeyValue("senderCurve2"),
                    senderSigningKey = Ed25519KeyValue("senderEd2"),
                    sessionId = "session2",
                    roomId = RoomId("room", "server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled2"
                )
            )

            cut.init(this)

            cut.updateOlmAccount { "olm_account" }

            eventually(5.seconds) {
                olmAccountRepository.get(1) shouldBe "olm_account"
                cut.notBackedUpInboundMegolmSessions.value.size shouldBe 2
            }
        }

    @Test
    fun `updateInboundMegolmSession » add and remove to notBackedUpInboundMegolmSessions`() = runTest {
        cut.updateInboundMegolmSession(session.sessionId, session.roomId) { session }
        cut.notBackedUpInboundMegolmSessions.value.values shouldBe setOf(session)
        cut.updateInboundMegolmSession(session.sessionId, session.roomId) {
            session.copy(hasBeenBackedUp = true)
        }
        cut.notBackedUpInboundMegolmSessions.value.values.shouldBeEmpty()
    }
}