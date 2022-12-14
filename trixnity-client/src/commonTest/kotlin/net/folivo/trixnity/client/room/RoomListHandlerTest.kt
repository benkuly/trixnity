package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds

class RoomListHandlerTest : ShouldSpec({
    timeout = 10_000
    val alice = UserId("alice", "server")
    val room = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomListHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomListHandler(
            api,
            roomStore,
            MatrixClientConfiguration(),
        )
    }

    afterTest {
        scope.cancel()
    }

    fun textEvent(i: Long = 24): MessageEvent<TextMessageEventContent> {
        return MessageEvent(
            TextMessageEventContent("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
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
})