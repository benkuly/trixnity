package net.folivo.trixnity.client.store

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import kotlin.time.Duration.Companion.seconds

class OlmStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var olmAccountRepository: OlmAccountRepository
    lateinit var inboundMegolmSessionRepository: InboundMegolmSessionRepository

    lateinit var storeScope: CoroutineScope
    lateinit var cut: OlmCryptoStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        olmAccountRepository = InMemoryOlmAccountRepository()
        inboundMegolmSessionRepository = InMemoryInboundMegolmSessionRepository()
        cut = OlmCryptoStore(
            olmAccountRepository,
            InMemoryOlmForgetFallbackKeyAfterRepository(),
            InMemoryOlmSessionRepository(),
            inboundMegolmSessionRepository,
            InMemoryInboundMegolmMessageIndexRepository(),
            InMemoryOutboundMegolmSessionRepository(),
            RepositoryTransactionManagerMock(),
            MatrixClientConfiguration(),
            ObservableCacheStatisticCollector(),
            storeScope,
            Clock.System,
        )
    }
    afterTest {
        storeScope.cancel()
    }
    context(OlmCryptoStore::init.name) {
        should("load values from database") {
            olmAccountRepository.save(1, "olm_account")

            cut.init(this)

            cut.updateOlmAccount { "olm_account" }
        }
        should("start job, which saves changes to database and fills notBackedUp inbound megolm sessions") {
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
    }
    context(OlmCryptoStore::updateInboundMegolmSession.name) {
        val session = StoredInboundMegolmSession(
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
        should("add and remove to ${OlmCryptoStore::notBackedUpInboundMegolmSessions.name}") {
            cut.updateInboundMegolmSession(session.sessionId, session.roomId) { session }
            cut.notBackedUpInboundMegolmSessions.value.values shouldBe setOf(session)
            cut.updateInboundMegolmSession(session.sessionId, session.roomId) {
                session.copy(hasBeenBackedUp = true)
            }
            cut.notBackedUpInboundMegolmSessions.value.values.shouldBeEmpty()
        }
    }
})