package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedRoomRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedRoomRepository
    lateinit var rtm: ExposedRepositoryTransactionManager

    beforeTest {
        val db = createDatabase()
        rtm = ExposedRepositoryTransactionManager(db)
        newSuspendedTransaction {
            SchemaUtils.create(ExposedRoom)
        }
        cut = ExposedRoomRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key2, lastEventId = null)
        val room2Copy = room2.copy(lastEventId = EventId("\$Event2"))

        rtm.writeTransaction {
            cut.save(key1, room1)
            cut.save(key2, room2)
            cut.get(key1) shouldBe room1
            cut.get(key2) shouldBe room2
            cut.save(key2, room2Copy)
            cut.get(key2) shouldBe room2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("get all") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key1, lastEventId = null)

        rtm.writeTransaction {
            cut.save(key1, room1)
            cut.save(key2, room2)
            cut.getAll() shouldContainAll listOf(room1, room2)
        }
    }
})