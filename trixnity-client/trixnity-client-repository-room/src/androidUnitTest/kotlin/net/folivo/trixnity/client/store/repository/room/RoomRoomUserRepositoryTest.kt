package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomUserRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomUserRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomUserRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val user1 = RoomUser(
            key1, UserId("alice", "server"), "ALIC", Event.StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event1"),
                UserId("alice", "server"),
                key1,
                1234,
                stateKey = "@alice:server"
            )
        )
        val user2 = RoomUser(
            key1, UserId("bob", "server"), "BO", Event.StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("\$event2"),
                UserId("alice", "server"),
                key2,
                1234,
                stateKey = "@bob:server"
            )
        )
        val user3 = RoomUser(
            key1, UserId("cedric", "server"), "CEDRIC", Event.StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event3"),
                UserId("cedric", "server"),
                key2,
                1234,
                stateKey = "@cedric:server"
            )
        )

        repo.save(key1, mapOf(user1.userId to user1))
        repo.save(key2, mapOf(user2.userId to user2))
        repo.get(key1) shouldBe mapOf(user1.userId to user1)
        repo.get(key2) shouldBe mapOf(user2.userId to user2)
        repo.save(key2, mapOf(user2.userId to user2, user3.userId to user3))
        repo.get(key2) shouldBe mapOf(user2.userId to user2, user3.userId to user3)
        repo.delete(key1)
        repo.get(key1) shouldHaveSize 0
    }

    @Test
    fun `Save and get by second key`() = runTest {
        val key = RoomId("room3", "server")
        val user = RoomUser(
            key, UserId("alice2", "server"), "ALIC", Event.StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event5"),
                UserId("alice2", "server"),
                key,
                1234,
                stateKey = "@alice2:server"
            )
        )

        repo.saveBySecondKey(key, user.userId, user)
        repo.getBySecondKey(key, user.userId) shouldBe user
    }
}
