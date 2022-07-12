package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class SqlDelightRoomRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightRoomRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightRoomRepository(Database(driver).roomQueries, createMatrixEventJson(), Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key1, lastEventId = null)
        val room2Copy = room2.copy(lastEventId = EventId("\$Event2"))

        cut.save(key1, room1)
        cut.save(key2, room2)
        cut.get(key1) shouldBe room1
        cut.get(key2) shouldBe room2
        cut.save(key2, room2Copy)
        cut.get(key2) shouldBe room2Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
    should("get all") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key1, lastEventId = null)

        cut.save(key1, room1)
        cut.save(key2, room2)
        cut.getAll() shouldContainExactly listOf(room1, room2)
    }
})