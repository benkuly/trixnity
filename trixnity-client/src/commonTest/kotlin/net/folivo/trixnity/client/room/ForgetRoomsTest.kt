package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
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

class ForgetRoomsTest : ShouldSpec({
    timeout = 15_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope

    lateinit var cut: ForgetRoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)
        cut = ForgetRoomServiceImpl(
            roomStore = roomStore,
            roomUserStore = roomUserStore,
            roomStateStore = roomStateStore,
            roomAccountDataStore = roomAccountDataStore,
            roomTimelineStore = roomTimelineStore,
            roomOutboxMessageStore = roomOutboxMessageStore
        )
    }

    afterTest {
        scope.cancel()
    }

    context(ForgetRoomService::invoke.name) {
        should("forget rooms when membershipt is leave") {
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

            cut(room)

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
        should("not forget rooms when membershipt is not leave") {
            roomStore.update(room) { simpleRoom.copy(room) }

            roomStore.getAll().first { it.size == 1 }

            cut(room)

            roomStore.get(room).first() shouldNotBe null
        }
    }
})