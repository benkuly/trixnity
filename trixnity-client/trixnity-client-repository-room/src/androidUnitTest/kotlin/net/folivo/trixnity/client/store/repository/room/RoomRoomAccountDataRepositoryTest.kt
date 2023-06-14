package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomAccountDataRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomAccountDataRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomAccountDataRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val accountDataEvent1 =
            mapOf("" to RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, ""))
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
        val accountDataEvent3 =
            mapOf("" to accountDataEvent2[""].shouldNotBeNull().copy(roomId = roomId1))

        repo.save(key1, accountDataEvent1)
        repo.save(key2, accountDataEvent2)
        repo.get(key1) shouldBe accountDataEvent1
        repo.get(key2) shouldBe accountDataEvent2
        repo.save(key2, accountDataEvent3)
        repo.get(key2) shouldBe accountDataEvent3
        repo.delete(key1)
        repo.get(key1) shouldHaveSize 0
    }

    @Test
    fun `Save and get by second key`() = runTest {
        val roomId = RoomId("someRoom", "server")
        val key = RoomAccountDataRepositoryKey(roomId, "m.fully_read")
        val accountDataEvent =
            RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId, "")
        repo.saveBySecondKey(key, "", accountDataEvent)
        repo.getBySecondKey(key, "") shouldBe accountDataEvent
    }

    @Test
    fun deleteByRoomId() = runTest {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val key3 = RoomAccountDataRepositoryKey(roomId1, "org.example.mynamespace")
        val accountDataEvent1 = mapOf("" to RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, ""))
        val accountDataEvent2 = mapOf("" to RoomAccountDataEvent(FullyReadEventContent(EventId("event2")), roomId2, ""))
        val accountDataEvent3 = mapOf("" to RoomAccountDataEvent(FullyReadEventContent(EventId("event3")), roomId1, ""))
        repo.save(key1, accountDataEvent1)
        repo.save(key2, accountDataEvent2)
        repo.save(key3, accountDataEvent3)
        repo.deleteByRoomId(roomId1)
        repo.get(key1) shouldHaveSize 0
        repo.get(key2) shouldBe accountDataEvent2
        repo.get(key3) shouldHaveSize 0
    }
}
