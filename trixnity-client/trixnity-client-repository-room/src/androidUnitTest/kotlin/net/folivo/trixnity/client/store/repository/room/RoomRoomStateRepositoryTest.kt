package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomStateRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomStateRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomStateRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val state1 = Event.StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@alice:server"
        )
        val state1Copy = state1.copy(id = EventId("$2event"))
        val state2 = Event.StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = Event.StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("celina", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )

        repo.save(key1, "@alice:server", state1)
        repo.save(key2, "@bob:server", state2)
        repo.save(key2, "@celina:server", state3)
        repo.get(key1, "@alice:server") shouldBe state1
        repo.get(key2, "@bob:server") shouldBe state2
        repo.save(key1, "@alice:server", state1Copy)
        repo.get(key1, "@alice:server") shouldBe state1Copy
        repo.delete(key1, "@alice:server")
        repo.get(key1) shouldHaveSize 0
        repo.get(key2) shouldBe mapOf(
            "@bob:server" to state2,
            "@celina:server" to state3
        )
    }

    @Test
    fun `Save and get by second key`() = runTest {
        val key = RoomStateRepositoryKey(RoomId("room3", "server"), "m.room.member")
        val event = Event.StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@cedric:server"
        )

        repo.save(key, "@cedric:server", event)
        repo.get(key, "@cedric:server") shouldBe event
    }

    @Test
    fun deleteByRoomId() = runTest {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val key3 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.name")

        val state1 = Event.StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@alice:server"
        )
        val state2 = Event.StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = Event.StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room1", "server"),
            originTimestamp = 24,
            stateKey = ""
        )

        repo.save(key1, "@alice:server", state1)
        repo.save(key2, "", state2)
        repo.save(key3, "", state3)
        repo.deleteByRoomId(RoomId("room1", "server"))
        repo.get(key1) shouldHaveSize 0
        repo.get(key2) shouldBe mapOf("" to state2)
        repo.get(key3) shouldHaveSize 0
    }
}
