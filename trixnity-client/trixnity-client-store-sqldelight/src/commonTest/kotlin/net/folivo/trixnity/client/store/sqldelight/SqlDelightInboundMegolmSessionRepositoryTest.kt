package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

class SqlDelightInboundMegolmSessionRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightInboundMegolmSessionRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightInboundMegolmSessionRepository(
            Database(driver).olmQueries,
            createMatrixEventJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val inboundSessionKey1 = InboundMegolmSessionRepositoryKey("session1", roomId)
        val inboundSessionKey2 = InboundMegolmSessionRepositoryKey("session2", roomId)
        val inboundSession1 =
            StoredInboundMegolmSession(
                senderKey = Curve25519Key(null, "curve1"),
                sessionId = "session1",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = false,
                isTrusted = false,
                senderSigningKey = Key.Ed25519Key(null, "ed1"),
                forwardingCurve25519KeyChain = listOf(
                    Curve25519Key(null, "curveExt1"),
                    Curve25519Key(null, "curveExt2")
                ),
                pickled = "pickle1"
            )
        val inboundSession2 =
            StoredInboundMegolmSession(
                senderKey = Curve25519Key(null, "curve2"),
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

        cut.save(inboundSessionKey1, inboundSession1)
        cut.save(inboundSessionKey2, inboundSession2)
        cut.get(inboundSessionKey1) shouldBe inboundSession1
        cut.get(inboundSessionKey2) shouldBe inboundSession2
        cut.getByNotBackedUp() shouldBe setOf(inboundSession1)
        cut.save(inboundSessionKey2, inboundSession2Copy)
        cut.get(inboundSessionKey2) shouldBe inboundSession2Copy
        cut.delete(inboundSessionKey1)
        cut.get(inboundSessionKey1) shouldBe null
    }
})