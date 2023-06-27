package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson


class RealmRoomStateRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmRoomStateRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmRoomState::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmRoomStateRepository(createMatrixEventJson())
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

        writeTransaction(realm) {
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

        writeTransaction(realm) {
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

        writeTransaction(realm) {
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