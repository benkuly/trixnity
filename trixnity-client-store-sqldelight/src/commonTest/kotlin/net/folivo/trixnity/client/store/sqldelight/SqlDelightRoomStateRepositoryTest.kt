package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightRoomStateRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightRoomStateRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightRoomStateRepository(Database(driver).roomStateQueries, createMatrixJson(), Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = RoomStateRepositoryKey(RoomId("room1", "server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("room2", "server"), "m.room.name")
        val state1 = mapOf(
            "@alice:server" to StateEvent(
                MemberEventContent(membership = JOIN),
                EventId("\$event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@alice:server"
            )
        )
        val state1Copy = state1 + mapOf(
            "@bob:server" to StateEvent(
                MemberEventContent(membership = LEAVE),
                EventId("\$event"),
                UserId("alice", "server"),
                RoomId("room1", "server"),
                1234,
                stateKey = "@bob:server"
            )
        )
        val state2 = mapOf(
            "" to StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("bob", "server"),
                RoomId("room2", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
        )

        cut.save(key1, state1)
        cut.save(key2, state2)
        cut.get(key1) shouldBe state1
        cut.get(key2) shouldBe state2
        cut.save(key1, state1Copy)
        cut.get(key1) shouldBe state1Copy
        cut.delete(key1)
        cut.get(key1) shouldHaveSize 0
    }
    should("save and get by state key") {
        val key = RoomStateRepositoryKey(RoomId("room3", "server"), "m.room.member")
        val event = StateEvent(
            MemberEventContent(membership = JOIN),
            EventId("\$event"),
            UserId("alice", "server"),
            RoomId("room1", "server"),
            1234,
            stateKey = "@cedric:server"
        )
        cut.saveBySecondKey(key, "@cedric:server", event)
        cut.getBySecondKey(key, "@cedric:server") shouldBe event
    }
})