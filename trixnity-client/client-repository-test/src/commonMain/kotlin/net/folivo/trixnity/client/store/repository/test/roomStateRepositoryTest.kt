package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import org.koin.core.Koin


fun ShouldSpec.roomStateRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomStateRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomStateRepositoryTest: save, get and delete") {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val state1 = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@alice:server"
        )
        val state1Copy = state1.copy(id = EventId("$2event"))
        val state2 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("celina", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )

        rtm.writeTransaction {
            cut.save(key1, "@alice:server", state1)
            cut.save(key2, "@bob:server", state2)
            cut.save(key2, "@celina:server", state3)
            cut.get(key1, "@alice:server") shouldBe state1
            cut.get(key2, "@bob:server") shouldBe state2
            cut.save(key1, "@alice:server", state1Copy)
            cut.get(key1, "@alice:server") shouldBe state1Copy
            cut.delete(key1, "@alice:server")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "@bob:server" to state2,
                "@celina:server" to state3
            )
        }
    }
    should("roomStateRepositoryTest: save and get by second key") {
        val key = RoomStateRepositoryKey(RoomId("room3", "server"), "m.room.member")
        val event = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@cedric:server"
        )

        rtm.writeTransaction {
            cut.save(key, "@cedric:server", event)
            cut.get(key, "@cedric:server") shouldBe event
        }
    }

    should("roomStateRepositoryTest: deleteByRoomId") {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val key3 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.name")

        val state1 = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@alice:server"
        )
        val state2 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room2", "server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("room1", "server"),
            originTimestamp = 24,
            stateKey = ""
        )

        rtm.writeTransaction {
            cut.save(key1, "@alice:server", state1)
            cut.save(key2, "", state2)
            cut.save(key3, "", state3)
            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf("" to state2)
            cut.get(key3) shouldHaveSize 0
        }
    }
}