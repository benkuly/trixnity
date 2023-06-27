package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedRoomStateRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedRoomStateRepository
    lateinit var rtm: ExposedRepositoryTransactionManager

    beforeTest {
        val db = createDatabase()
        rtm = ExposedRepositoryTransactionManager(db)
        newSuspendedTransaction {
            SchemaUtils.create(ExposedRoomState)
        }
        cut = ExposedRoomStateRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val state1 = mapOf(
            "@alice:server" to StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("$1event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@alice:server"
            )
        )
        val state1Copy = state1 + mapOf(
            "@bob:server" to StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("$1event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@bob:server"
            )
        )
        val state2 = mapOf(
            "" to StateEvent(
                NameEventContent("room name"),
                EventId("$2eventId"),
                UserId("bob", "server"),
                RoomId("room2", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
        )

        rtm.writeTransaction {
            cut.save(key1, state1)
            cut.save(key2, state2)
            cut.get(key1) shouldBe state1
            cut.get(key2) shouldBe state2
            cut.save(key1, state1Copy)
            cut.get(key1) shouldBe state1Copy
            cut.delete(key1)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("save and get by second key") {
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
            cut.saveBySecondKey(key, "@cedric:server", event)
            cut.getBySecondKey(key, "@cedric:server") shouldBe event
        }
    }
    should("deleteByRoomId") {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val key3 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.name")

        val state1 = mapOf(
            "@alice:server" to StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("$1event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@alice:server"
            )
        )
        val state2 = mapOf(
            "" to StateEvent(
                NameEventContent("room name"),
                EventId("$2eventId"),
                UserId("bob", "server"),
                RoomId("room2", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
        )
        val state3 = mapOf(
            "" to StateEvent(
                NameEventContent("room name"),
                EventId("$2eventId"),
                UserId("bob", "server"),
                RoomId("room1", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
        )

        rtm.writeTransaction {
            cut.save(key1, state1)
            cut.save(key2, state2)
            cut.save(key3, state3)
            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe state2
            cut.get(key3) shouldHaveSize 0
        }
    }
})