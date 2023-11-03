package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import org.koin.core.Koin


fun ShouldSpec.roomUserRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomUserRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomUserRepositoryTest: save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val user1 = RoomUser(
            key1, UserId("alice", "server"), "ALIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event1"),
                UserId("alice", "server"),
                key1,
                1234,
                stateKey = "@alice:server"
            )
        )
        val user2 = RoomUser(
            key1, UserId("bob", "server"), "BO", StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("\$event2"),
                UserId("alice", "server"),
                key2,
                1234,
                stateKey = "@bob:server"
            )
        )
        val user3 = RoomUser(
            key1, UserId("cedric", "server"), "CEDRIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event3"),
                UserId("cedric", "server"),
                key2,
                1234,
                stateKey = "@cedric:server"
            )
        )

        rtm.writeTransaction {
            cut.save(key1, user1.userId, user1)
            cut.save(key2, user2.userId, user2)
            cut.get(key1) shouldBe mapOf(user1.userId to user1)
            cut.get(key1, user1.userId) shouldBe user1
            cut.get(key2) shouldBe mapOf(user2.userId to user2)
            cut.save(key2, user3.userId, user3)
            cut.get(key2) shouldBe mapOf(user2.userId to user2, user3.userId to user3)
            cut.delete(key1, user1.userId)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("roomUserRepositoryTest: save and get by second key") {
        val key = RoomId("room1", "server")
        val user = RoomUser(
            key, UserId("alice", "server"), "ALIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event1"),
                UserId("alice", "server"),
                key,
                1234,
                stateKey = "@alice:server"
            )
        )

        rtm.writeTransaction {
            cut.save(key, user.userId, user)
            cut.get(key, user.userId) shouldBe user
        }
    }
}