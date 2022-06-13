package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedInboundMegolmMessageIndexRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedInboundMegolmMessageIndexRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedInboundMegolmMessageIndex)
        }
        cut = ExposedInboundMegolmMessageIndexRepository()
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val messageIndexKey1 =
            InboundMegolmMessageIndexRepositoryKey(Curve25519Key(null, "curve1"), "session1", roomId, 24)
        val messageIndexKey2 =
            InboundMegolmMessageIndexRepositoryKey(Curve25519Key(null, "curve2"), "session2", roomId, 12)
        val messageIndex1 = StoredInboundMegolmMessageIndex(
            Curve25519Key(null, "curve1"), "session1", roomId, 24,
            EventId("event"),
            1234
        )
        val messageIndex2 = StoredInboundMegolmMessageIndex(
            Curve25519Key(null, "curve2"), "session2", roomId, 12,
            EventId("event"),
            1234
        )
        val messageIndex2Copy = messageIndex2.copy(originTimestamp = 1235)

        newSuspendedTransaction {
            cut.save(messageIndexKey1, messageIndex1)
            cut.save(messageIndexKey2, messageIndex2)
            cut.get(messageIndexKey1) shouldBe messageIndex1
            cut.get(messageIndexKey2) shouldBe messageIndex2
            cut.save(messageIndexKey2, messageIndex2Copy)
            cut.get(messageIndexKey2) shouldBe messageIndex2Copy
            cut.delete(messageIndexKey1)
            cut.get(messageIndexKey1) shouldBe null
        }
    }
})