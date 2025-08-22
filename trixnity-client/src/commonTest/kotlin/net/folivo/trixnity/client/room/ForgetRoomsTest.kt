package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test
import kotlin.time.Clock

class ForgetRoomsTest : TrixnityBaseTest() {

    private val room = simpleRoom.roomId

    private val roomStore = getInMemoryRoomStore()
    private val roomUserStore = getInMemoryRoomUserStore()
    private val roomStateStore = getInMemoryRoomStateStore()
    private val roomAccountDataStore = getInMemoryRoomAccountDataStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()
    private val roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore()

    private val cut = ForgetRoomServiceImpl(
        roomStore = roomStore,
        roomUserStore = roomUserStore,
        roomStateStore = roomStateStore,
        roomAccountDataStore = roomAccountDataStore,
        roomTimelineStore = roomTimelineStore,
        roomOutboxMessageStore = roomOutboxMessageStore,
    )

    @Test
    fun `invoke » forget rooms when membership is leave`() = runTest {
        roomStore.update(room) { simpleRoom.copy(room, membership = Membership.LEAVE) }

        fun timelineEvent(roomId: RoomId, i: Int) =
            TimelineEvent(
                MessageEvent(
                    RoomMessageEventContent.TextBased.Text("$i"),
                    EventId("$i"),
                    UserId("sender", "server"),
                    roomId,
                    1234L,
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
        roomTimelineStore.addAll(
            listOf(
                timelineEvent(room, 1),
                timelineEvent(room, 2),
            )
        )

        fun timelineEventRelation(roomId: RoomId, i: Int) =
            TimelineEventRelation(roomId, EventId("r$i"), RelationType.Replace, EventId("$i"))
        roomTimelineStore.addRelation(timelineEventRelation(room, 1))
        roomTimelineStore.addRelation(timelineEventRelation(room, 2))

        fun stateEvent(roomId: RoomId, i: Int) =
            StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("$i"),
                UserId("sender", "server"),
                roomId,
                1234L,
                stateKey = "$i",
            )
        roomStateStore.save(stateEvent(room, 1))
        roomStateStore.save(stateEvent(room, 2))

        fun roomAccountDataEvent(roomId: RoomId, i: Int) =
            RoomAccountDataEvent(
                FullyReadEventContent(EventId("$i")),
                roomId,
                key = "$i",
            )
        roomAccountDataStore.save(roomAccountDataEvent(room, 1))
        roomAccountDataStore.save(roomAccountDataEvent(room, 2))

        fun roomUser(roomId: RoomId, i: Int) =
            RoomUser(roomId, UserId("user$i", "server"), "$i", stateEvent(roomId, i))
        roomUserStore.update(UserId("1"), room) { roomUser(room, 1) }
        roomUserStore.update(UserId("2"), room) { roomUser(room, 2) }

        roomOutboxMessageStore.update(room, "t1") {
            RoomOutboxMessage(
                room, "t1", RoomMessageEventContent.TextBased.Text("hi"),
                Clock.System.now()
            )
        }

        roomStore.getAll().first { it.size == 1 }

        cut(room, false)

        roomStore.get(room).first() shouldBe null

        roomTimelineStore.get(EventId("1"), room).first() shouldBe null
        roomTimelineStore.get(EventId("2"), room).first() shouldBe null

        roomTimelineStore.getRelations(EventId("1"), room, RelationType.Replace)
            .first().values.firstOrNull()?.first() shouldBe null
        roomTimelineStore.getRelations(EventId("2"), room, RelationType.Replace)
            .first().values.firstOrNull()?.first() shouldBe null

        roomStateStore.getByStateKey<MemberEventContent>(room, "1").first() shouldBe null
        roomStateStore.getByStateKey<MemberEventContent>(room, "2").first() shouldBe null

        roomAccountDataStore.get<FullyReadEventContent>(room, "1").first() shouldBe null
        roomAccountDataStore.get<FullyReadEventContent>(room, "2").first() shouldBe null

        roomUserStore.get(UserId("1"), room).first() shouldBe null
        roomUserStore.get(UserId("2"), room).first() shouldBe null

        roomOutboxMessageStore.get(room, "t1").first() shouldBe null
    }

    @Test
    fun `invoke » not forget rooms when membership is not leave`() = runTest {
        roomStore.update(room) { simpleRoom.copy(room) }
        roomStore.getAll().first { it.size == 1 }
        cut(room, false)
        roomStore.get(room).first() shouldNotBe null
    }

    @Test
    fun `invoke » forget rooms when membership is not leave if forced`() = runTest {
        roomStore.update(room) { simpleRoom.copy(room) }
        roomStore.getAll().first { it.size == 1 }
        cut(room, true)
        roomStore.get(room).first() shouldBe null
    }

}
