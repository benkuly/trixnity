package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class SqlDelightRoomAccountDataRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightRoomAccountDataRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightRoomAccountDataRepository(
            Database(driver).roomAccountDataQueries,
            createMatrixEventJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }

    should("save, get and delete") {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val accountDataEvent1 = mapOf("" to RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, ""))
        val accountDataEvent2 = mapOf(
            "" to RoomAccountDataEvent(
                UnknownRoomAccountDataEventContent(
                    JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                    "org.example.mynamespace"
                ),
                roomId2,
                ""
            )
        )
        val accountDataEvent3 = mapOf("" to accountDataEvent2[""]!!.copy(roomId = roomId1))

        cut.save(key1, accountDataEvent1)
        cut.save(key2, accountDataEvent2)
        cut.get(key1) shouldBe accountDataEvent1
        cut.get(key2) shouldBe accountDataEvent2
        cut.save(key2, accountDataEvent3)
        cut.get(key2) shouldBe accountDataEvent3
        cut.delete(key1)
        cut.get(key1)?.shouldHaveSize(0)
    }
    should("save and get by second key") {
        val roomId = RoomId("someRoom", "server")
        val key = RoomAccountDataRepositoryKey(roomId, "m.fully_read")
        val accountDataEvent = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId, "")
        cut.saveBySecondKey(key, "", accountDataEvent)
        cut.getBySecondKey(key, "") shouldBe accountDataEvent
    }
})