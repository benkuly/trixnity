package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedRoomAccountDataRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedRoomAccountDataRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedRoomAccountData)
        }
        cut = ExposedRoomAccountDataRepository(createMatrixEventJson())
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
        val accountDataEvent3 = mapOf("" to accountDataEvent2[""].shouldNotBeNull().copy(roomId = roomId1))

        newSuspendedTransaction {
            cut.save(key1, accountDataEvent1)
            cut.save(key2, accountDataEvent2)
            cut.get(key1) shouldBe accountDataEvent1
            cut.get(key2) shouldBe accountDataEvent2
            cut.save(key2, accountDataEvent3)
            cut.get(key2) shouldBe accountDataEvent3
            cut.delete(key1)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("save and get by second key") {
        val roomId = RoomId("someRoom", "server")
        val key = RoomAccountDataRepositoryKey(roomId, "m.fully_read")
        val accountDataEvent = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId, "")
        newSuspendedTransaction {
            cut.saveBySecondKey(key, "", accountDataEvent)
            cut.getBySecondKey(key, "") shouldBe accountDataEvent
        }
    }
})