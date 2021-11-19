package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key

class SqlDelightInboundMegolmMessageIndexRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightInboundMegolmMessageIndexRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightInboundMegolmMessageIndexRepository(Database(driver).olmQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
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

        cut.save(messageIndexKey1, messageIndex1)
        cut.save(messageIndexKey2, messageIndex2)
        cut.get(messageIndexKey1) shouldBe messageIndex1
        cut.get(messageIndexKey2) shouldBe messageIndex2
        cut.save(messageIndexKey2, messageIndex2Copy)
        cut.get(messageIndexKey2) shouldBe messageIndex2Copy
        cut.delete(messageIndexKey1)
        cut.get(messageIndexKey1) shouldBe null
    }
})