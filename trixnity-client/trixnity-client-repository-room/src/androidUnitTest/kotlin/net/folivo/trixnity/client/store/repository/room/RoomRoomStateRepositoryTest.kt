package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
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
        val state1 = mapOf(
            "@alice:server" to Event.StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("$1event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@alice:server"
            )
        )
        val state1Copy = state1 + mapOf(
            "@bob:server" to Event.StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("$1event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@bob:server"
            )
        )
        val state2 = mapOf(
            "" to Event.StateEvent(
                NameEventContent("room name"),
                EventId("$2eventId"),
                UserId("bob", "server"),
                RoomId("room2", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
        )

        repo.save(key1, state1)
        repo.save(key2, state2)
        repo.get(key1) shouldBe state1
        repo.get(key2) shouldBe state2
        repo.save(key1, state1Copy)
        repo.get(key1) shouldBe state1Copy
        repo.delete(key1)
        repo.get(key1) shouldHaveSize 0
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

        repo.saveBySecondKey(key, "@cedric:server", event)
        repo.getBySecondKey(key, "@cedric:server") shouldBe event
    }
}
