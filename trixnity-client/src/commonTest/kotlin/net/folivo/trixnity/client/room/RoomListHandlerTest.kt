package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.LeftRoom
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.RelationType
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomListHandlerTest : ShouldSpec({
    timeout = 10_000
    val room = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var config: MatrixClientConfiguration
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomListHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        config = MatrixClientConfiguration()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomListHandler(
            api,
            roomStore,
            roomTimelineStore,
            roomStateStore,
            roomAccountDataStore,
            roomUserStore,
            config,
        )
    }

    afterTest {
        scope.cancel()
    }

    context(RoomListHandler::handleSyncResponse.name) {
        context("unreadMessageCount") {
            should("set unread message count") {
                cut.handleSyncResponse(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            join = mapOf(
                                room to JoinedRoom(
                                    unreadNotifications = JoinedRoom.UnreadNotificationCounts(notificationCount = 24)
                                )
                            ),
                        )
                    )
                )
                roomStore.get(room).first()?.unreadMessageCount shouldBe 24
            }
        }
        context("lastRelevantEventId") {
            should("setlastRelevantEventId ") {
                cut.handleSyncResponse(
                    Sync.Response(
                        room = Sync.Response.Rooms(
                            join = mapOf(
                                room to JoinedRoom(
                                    timeline = Sync.Response.Rooms.Timeline(
                                        events = listOf(
                                            Event.StateEvent(
                                                CreateEventContent(UserId("user1", "localhost")),
                                                EventId("event1"),
                                                UserId("user1", "localhost"),
                                                room,
                                                0,
                                                stateKey = ""
                                            ),
                                            MessageEvent(
                                                TextMessageEventContent("Hello!"),
                                                EventId("event2"),
                                                UserId("user1", "localhost"),
                                                room,
                                                5,
                                            ),
                                            Event.StateEvent(
                                                AvatarEventContent("mxc://localhost/123456"),
                                                EventId("event3"),
                                                UserId("user1", "localhost"),
                                                room,
                                                10,
                                                stateKey = ""
                                            ),
                                        ), previousBatch = "abcdef"
                                    )
                                )
                            )
                        ), nextBatch = "123456"
                    )
                )
                roomStore.get(room).first()?.lastRelevantEventId shouldBe EventId("event2")
            }
        }
    }
    context(RoomListHandler::handleAfterSyncResponse.name) {
        should("forget rooms on leave when activated") {
            roomStore.update(room) { simpleRoom.copy(room, membership = Membership.LEAVE) }

            fun timelineEvent(roomId: RoomId, i: Int) =
                TimelineEvent(
                    MessageEvent(
                        TextMessageEventContent("$i"),
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
                Event.StateEvent(
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
                Event.RoomAccountDataEvent(
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

            roomStore.getAll().first { it.size == 1 }

            cut.handleAfterSyncResponse(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = mapOf(room to LeftRoom())
                    )
                )
            )

            roomStore.get(room).first() shouldBe null

            roomTimelineStore.get(EventId("1"), room).first() shouldBe null
            roomTimelineStore.get(EventId("2"), room).first() shouldBe null

            roomTimelineStore.getRelations(EventId("1"), room, RelationType.Replace).first() shouldBe null
            roomTimelineStore.getRelations(EventId("2"), room, RelationType.Replace).first() shouldBe null

            roomStateStore.getByStateKey<MemberEventContent>(room, "1").first() shouldBe null
            roomStateStore.getByStateKey<MemberEventContent>(room, "2").first() shouldBe null

            roomAccountDataStore.get<FullyReadEventContent>(room, "1").first() shouldBe null
            roomAccountDataStore.get<FullyReadEventContent>(room, "2").first() shouldBe null

            roomUserStore.get(UserId("1"), room).first() shouldBe null
            roomUserStore.get(UserId("2"), room).first() shouldBe null
        }
        should("not forget rooms on leave when disabled") {
            config.deleteRoomsOnLeave = false
            roomStore.update(room) { simpleRoom.copy(membership = Membership.LEAVE) }

            roomStore.getAll().first { it.size == 1 }

            cut.handleAfterSyncResponse(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = mapOf(room to LeftRoom())
                    )
                )
            )

            roomStore.get(room).first() shouldNotBe null
        }
    }
})