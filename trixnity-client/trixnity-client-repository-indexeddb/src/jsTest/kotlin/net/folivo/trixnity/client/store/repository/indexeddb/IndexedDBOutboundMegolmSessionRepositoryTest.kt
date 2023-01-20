package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

class IndexedDBOutboundMegolmSessionRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBOutboundMegolmSessionRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBOutboundMegolmSessionRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBOutboundMegolmSessionRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val session1 = StoredOutboundMegolmSession(key1, pickled = "1")
        val session2 = StoredOutboundMegolmSession(key2, pickled = "2")
        val session2Copy = session2.copy(
            newDevices = mapOf(
                UserId("bob", "server") to setOf("Device1"),
                UserId("alice", "server") to setOf("Device2", "Device3")
            )
        )

        rtm.writeTransaction {
            cut.save(key1, session1)
            cut.save(key2, session2)
            cut.get(key1) shouldBe session1
            cut.get(key2) shouldBe session2
            cut.save(key2, session2Copy)
            cut.get(key2) shouldBe session2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})