package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedRoomUserRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedRoomUserRepository
    lateinit var rtm: ExposedRepositoryTransactionManager

    beforeTest {
        val db = createDatabase()
        rtm = ExposedRepositoryTransactionManager(db)
        newSuspendedTransaction {
            SchemaUtils.create(ExposedRoomUser)
        }
        cut = ExposedRoomUserRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
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

        rtm.writeTransaction {
            cut.save(key1, mapOf(user1.userId to user1))
            cut.save(key2, mapOf(user2.userId to user2))
            cut.get(key1) shouldBe mapOf(user1.userId to user1)
            cut.get(key2) shouldBe mapOf(user2.userId to user2)
            cut.save(key2, mapOf(user2.userId to user2, user3.userId to user3))
            cut.get(key2) shouldBe mapOf(user2.userId to user2, user3.userId to user3)
            cut.delete(key1)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("save and get by UserId") {
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

        rtm.writeTransaction {
            cut.saveBySecondKey(key, user.userId, user)
            cut.getBySecondKey(key, user.userId) shouldBe user
        }
    }
})