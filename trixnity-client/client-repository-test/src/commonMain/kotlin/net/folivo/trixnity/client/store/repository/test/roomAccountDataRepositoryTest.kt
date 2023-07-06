package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import org.koin.core.Koin


fun ShouldSpec.roomAccountDataRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomAccountDataRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomAccountDataRepositoryTest: save, get and delete") {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val accountDataEvent1 = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, "")
        val accountDataEvent2 = RoomAccountDataEvent(
            UnknownRoomAccountDataEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace"
            ),
            roomId2,
            ""
        )
        val accountDataEvent3 = RoomAccountDataEvent(FullyReadEventContent(EventId("event2")), roomId1, "bla")
        val accountDataEvent2Copy = accountDataEvent2.copy(roomId = roomId1)

        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key2, "bla", accountDataEvent3)
            cut.get(key1, "") shouldBe accountDataEvent1
            cut.get(key2, "") shouldBe accountDataEvent2
            cut.save(key2, "", accountDataEvent2Copy)
            cut.get(key2, "") shouldBe accountDataEvent2Copy
            cut.delete(key1, "")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "" to accountDataEvent2Copy,
                "bla" to accountDataEvent3
            )
        }
    }
    should("roomAccountDataRepositoryTest: deleteByRoomId") {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val key3 = RoomAccountDataRepositoryKey(roomId1, "org.example.mynamespace")
        val accountDataEvent1 = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, "")
        val accountDataEvent2 = RoomAccountDataEvent(FullyReadEventContent(EventId("event2")), roomId2, "")
        val accountDataEvent3 = RoomAccountDataEvent(FullyReadEventContent(EventId("event3")), roomId1, "")
        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key3, "", accountDataEvent3)
            cut.deleteByRoomId(roomId1)
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf("" to accountDataEvent2)
            cut.get(key3) shouldHaveSize 0
        }
    }
}