package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

class IndexedDBInboundMegolmMessageIndexRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBInboundMegolmMessageIndexRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBInboundMegolmMessageIndexRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBInboundMegolmMessageIndexRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val messageIndexKey1 =
            InboundMegolmMessageIndexRepositoryKey("session1", roomId, 24)
        val messageIndexKey2 =
            InboundMegolmMessageIndexRepositoryKey("session2", roomId, 12)
        val messageIndex1 = StoredInboundMegolmMessageIndex(
            "session1", roomId, 24,
            EventId("event"),
            1234
        )
        val messageIndex2 = StoredInboundMegolmMessageIndex(
            "session2", roomId, 12,
            EventId("event"),
            1234
        )
        val messageIndex2Copy = messageIndex2.copy(originTimestamp = 1235)

        rtm.writeTransaction {
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