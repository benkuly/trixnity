package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomInboundMegolmSessionRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomInboundMegolmSessionRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomInboundMegolmSessionRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val roomId = RoomId("room", "server")
        val inboundSessionKey1 = InboundMegolmSessionRepositoryKey("session1", roomId)
        val inboundSessionKey2 = InboundMegolmSessionRepositoryKey("session2", roomId)
        val inboundSession1 =
            StoredInboundMegolmSession(
                senderKey = Key.Curve25519Key(null, "curve1"),
                sessionId = "session1",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = false,
                isTrusted = false,
                senderSigningKey = Key.Ed25519Key(null, "ed1"),
                forwardingCurve25519KeyChain = listOf(
                    Key.Curve25519Key(null, "curveExt1"),
                    Key.Curve25519Key(null, "curveExt2")
                ),
                pickled = "pickle1"
            )
        val inboundSession2 =
            StoredInboundMegolmSession(
                senderKey = Key.Curve25519Key(null, "curve2"),
                sessionId = "session2",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = true,
                isTrusted = false,
                senderSigningKey = Key.Ed25519Key(null, "ed2"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = "pickle2"
            )
        val inboundSession2Copy = inboundSession2.copy(pickled = "pickle2Copy")

        repo.save(inboundSessionKey1, inboundSession1)
        repo.save(inboundSessionKey2, inboundSession2)
        repo.get(inboundSessionKey1) shouldBe inboundSession1
        repo.get(inboundSessionKey2) shouldBe inboundSession2
        repo.getByNotBackedUp() shouldBe setOf(inboundSession1)
        repo.save(inboundSessionKey2, inboundSession2Copy)
        repo.get(inboundSessionKey2) shouldBe inboundSession2Copy
        repo.delete(inboundSessionKey1)
        repo.get(inboundSessionKey1) shouldBe null
    }
}
