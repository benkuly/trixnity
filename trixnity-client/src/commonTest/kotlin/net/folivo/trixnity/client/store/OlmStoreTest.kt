package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.OlmAccountRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key

class OlmStoreTest : ShouldSpec({
    val olmAccountRepository = mockk<OlmAccountRepository>(relaxUnitFun = true)
    val inboundMegolmSessionRepository = mockk<InboundMegolmSessionRepository>(relaxUnitFun = true)

    lateinit var storeScope: CoroutineScope
    lateinit var cut: OlmStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        coEvery { inboundMegolmSessionRepository.getByNotBackedUp() } returns setOf()
        cut = OlmStore(
            olmAccountRepository,
            mockk(),
            inboundMegolmSessionRepository,
            mockk(),
            mockk(),
            NoopRepositoryTransactionManager,
            storeScope
        )
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }
    context(OlmStore::init.name) {
        should("load values from database") {
            coEvery { olmAccountRepository.get(1) } returns "olm_account"

            cut.init()

            cut.account.value shouldBe "olm_account"
        }
        should("start job, which saves changes to database and fills notBackedUp inbound megolm sessions") {
            coEvery { olmAccountRepository.get(1) } returns null
            coEvery { inboundMegolmSessionRepository.getByNotBackedUp() } returns setOf(
                StoredInboundMegolmSession(
                    senderKey = Key.Curve25519Key(null, "senderCurve1"),
                    senderSigningKey = Key.Ed25519Key(null, "senderEd1"),
                    sessionId = "session1",
                    roomId = RoomId("room", "server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled1"
                ),
                StoredInboundMegolmSession(
                    senderKey = Key.Curve25519Key(null, "senderCurve2"),
                    senderSigningKey = Key.Ed25519Key(null, "senderEd2"),
                    sessionId = "session2",
                    roomId = RoomId("room", "server"),
                    firstKnownIndex = 1,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = "pickled2"
                )
            )

            cut.init()

            cut.account.value = "olm_account"
            coVerify(timeout = 5_000) {
                olmAccountRepository.save(1, "olm_account")
            }
            cut.notBackedUpInboundMegolmSessions.value.size shouldBe 2
        }
    }
    context(OlmStore::updateInboundMegolmSession.name) {
        val session = StoredInboundMegolmSession(
            senderKey = Key.Curve25519Key(null, "senderCurve"),
            sessionId = "session",
            roomId = RoomId("room", "server"),
            firstKnownIndex = 24,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Key.Ed25519Key(null, "edKey"),
            forwardingCurve25519KeyChain = listOf(),
            pickled = "pickle"
        )
        should("add and remove to ${OlmStore::notBackedUpInboundMegolmSessions.name}") {
            coEvery { inboundMegolmSessionRepository.get(any()) } returns null
            cut.updateInboundMegolmSession(session.senderKey, session.sessionId, session.roomId) { session }
            cut.notBackedUpInboundMegolmSessions.value.values shouldBe setOf(session)
            cut.updateInboundMegolmSession(session.senderKey, session.sessionId, session.roomId) {
                session.copy(hasBeenBackedUp = true)
            }
            cut.notBackedUpInboundMegolmSessions.value.values.shouldBeEmpty()
        }
    }
})