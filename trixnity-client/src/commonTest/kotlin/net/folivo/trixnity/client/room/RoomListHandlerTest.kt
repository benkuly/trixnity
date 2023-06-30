package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.LeftRoom
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomListHandlerTest : ShouldSpec({
    timeout = 10_000
    val room = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var roomServiceMock: RoomServiceMock
    lateinit var config: MatrixClientConfiguration
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomListHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomServiceMock = RoomServiceMock()
        config = MatrixClientConfiguration()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomListHandler(
            api,
            roomStore,
            roomServiceMock,
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
            cut.handleAfterSyncResponse(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = mapOf(room to LeftRoom())
                    )
                )
            )

            roomServiceMock.forgetRooms.value shouldBe listOf(room)
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