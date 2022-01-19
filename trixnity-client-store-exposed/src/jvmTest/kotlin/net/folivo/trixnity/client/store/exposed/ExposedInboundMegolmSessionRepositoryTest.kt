package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedInboundMegolmSessionRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedInboundMegolmSessionRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedInboundMegolmSession)
        }
        cut = ExposedInboundMegolmSessionRepository()
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val inboundSessionKey1 = InboundMegolmSessionRepositoryKey(Curve25519Key(null, "curve1"), "session1", roomId)
        val inboundSessionKey2 = InboundMegolmSessionRepositoryKey(Curve25519Key(null, "curve2"), "session2", roomId)
        val inboundSession1 = StoredInboundMegolmSession(Curve25519Key(null, "curve1"), "session1", roomId, "pickle1")
        val inboundSession2 = StoredInboundMegolmSession(Curve25519Key(null, "curve2"), "session2", roomId, "pickle2")
        val inboundSession2Copy = inboundSession2.copy(pickled = "pickle2Copy")

        newSuspendedTransaction {
            cut.save(inboundSessionKey1, inboundSession1)
            cut.save(inboundSessionKey2, inboundSession2)
            cut.get(inboundSessionKey1) shouldBe inboundSession1
            cut.get(inboundSessionKey2) shouldBe inboundSession2
            cut.save(inboundSessionKey2, inboundSession2Copy)
            cut.get(inboundSessionKey2) shouldBe inboundSession2Copy
            cut.delete(inboundSessionKey1)
            cut.get(inboundSessionKey1) shouldBe null
        }
    }
})