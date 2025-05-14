package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomDisplayName
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.MarkedUnreadEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class RoomListHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val roomId = RoomId("room", "server")

    private val user1 = UserId("user1", "server")
    private val user2 = UserId("user2", "server")
    private val user3 = UserId("user3", "server")
    private val user4 = UserId("user4", "server")
    private val user5 = UserId("user5", "server")

    private val roomStore = getInMemoryRoomStore()
    private val roomStateStore = getInMemoryRoomStateStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()
    private val roomAccountDataStore = getInMemoryRoomAccountDataStore()

    private val config = MatrixClientConfiguration()
    private val forgetRooms = mutableListOf<RoomId>()
    private val api = mockMatrixClientServerApiClient()

    private val cut = RoomListHandler(
        api = api,
        roomStore = roomStore,
        roomStateStore = roomStateStore,
        globalAccountDataStore = globalAccountDataStore,
        roomAccountDataStore = roomAccountDataStore,
        forgetRoomService = { forgetRooms.add(it) },
        userInfo = simpleUserInfo,
        tm = TransactionManagerMock(),
        config = config,
    )

    @Test
    fun `updateRoomList » unreadMessageCount » set unread message count`() = runTest {
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        join = mapOf(
                            roomId to JoinedRoom(
                                unreadNotifications = JoinedRoom.UnreadNotificationCounts(notificationCount = 24)
                            )
                        ),
                    )
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.unreadMessageCount shouldBe 24
    }

    @Test
    fun `updateRoomList » markedUnread » not mark as unread when not set`() = runTest {
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(join = mapOf(roomId to JoinedRoom()))
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.markedUnread shouldBe false
    }

    @Test
    fun `updateRoomList » markedUnread » mark as unread when set`() = runTest {
        roomAccountDataStore.save(ClientEvent.RoomAccountDataEvent(MarkedUnreadEventContent(true), roomId))
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(join = mapOf(roomId to JoinedRoom()))
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.markedUnread shouldBe true

    }

    @Test
    fun `updateRoomList » lastRelevantEventId » setlastRelevantEventId `() = runTest {
        cut.updateRoomList(
            SyncEvents(
                Sync.Response(
                    room = Sync.Response.Rooms(
                        join = mapOf(
                            roomId to JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(
                                        StateEvent(
                                            CreateEventContent(UserId("user1", "localhost")),
                                            EventId("event1"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            0,
                                            stateKey = ""
                                        ),
                                        MessageEvent(
                                            RoomMessageEventContent.TextBased.Text("Hello!"),
                                            EventId("event2"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            5,
                                        ),
                                        StateEvent(
                                            AvatarEventContent("mxc://localhost/123456"),
                                            EventId("event3"),
                                            UserId("user1", "localhost"),
                                            roomId,
                                            10,
                                            stateKey = ""
                                        ),
                                    ), previousBatch = "abcdef"
                                )
                            )
                        )
                    ), nextBatch = "123456"
                ),
                emptyList()
            )
        )
        roomStore.get(roomId).first()?.lastRelevantEventId shouldBe EventId("event2")
    }

    @Test
    fun `isDirect » set the room to direct == 'true' when a DirectEventContent is found for the room`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, isDirect = false) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), roomId)
                )
            )
            roomStore.getAll().first { it.size == 1 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.isDirect shouldBe true
        }

    @Test
    fun `isDirect » membership is LEAVE or BAN » don't change isDirect`() =
        runTest {
            val roomId2 = RoomId("room2", "server")
            roomStore.update(roomId) { Room(roomId, isDirect = true, membership = Membership.LEAVE) }
            roomStore.update(roomId2) { Room(roomId2, isDirect = true, membership = Membership.BAN) }
            val eventContent = DirectEventContent(
                mappings = mapOf()
            )
            roomStore.getAll().first { it.size == 2 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.isDirect shouldBe true
            roomStore.get(roomId2).first()?.isDirect shouldBe true
        }

    @Test
    fun `isDirect » set the room to direct == 'false' when no DirectEventContent is found for the room`() =
        runTest {
            val room1 = RoomId("room1", "localhost")
            val room2 = RoomId("room2", "localhost")
            val roomStore = roomStore

            roomStore.update(room1) { Room(room1, isDirect = true) }
            roomStore.update(room2) { Room(room2, isDirect = true) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(room2)
                )
            )
            roomStore.getAll().first { it.size == 2 }

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(room1).first()?.isDirect shouldBe false
            roomStore.get(room2).first()?.isDirect shouldBe true
        }


    @Test
    fun `avatarUrl » room is direct » set the avatar URL to a member's avatar URL`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = null) }
        roomStateStore.save(
            StateEvent(
                MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                EventId("1"),
                bob,
                roomId,
                0L,
                stateKey = alice.full,
            )
        )
        val eventContent = DirectEventContent(
            mappings = mapOf(
                alice to setOf(
                    roomId,
                    RoomId("room2", "localhost")
                )
            )
        )

        cut.updateRoomList(
            SyncEvents(
                Sync.Response(""),
                listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
            )
        )

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
    }

    @Test
    fun `avatarUrl » membership is LEAVE or BAN » don't change avatar'`() = runTest {
        val roomId2 = RoomId("room2", "localhost")
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef", membership = Membership.LEAVE) }
        roomStore.update(roomId2) { Room(roomId2, avatarUrl = "mxc://localhost/abcdef", membership = Membership.BAN) }
        val eventContent = DirectEventContent(
            mappings = mapOf()
        )

        cut.updateRoomList(
            SyncEvents(
                Sync.Response(""),
                listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
            )
        )

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
        roomStore.get(roomId2).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
    }

    @Test
    fun `avatarUrl » room is direct » update the room's avatar URL`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/old") }
        val event1 = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/right",
                membership = Membership.JOIN,
            ),
            EventId("1"),
            alice,
            roomId,
            0L,
            stateKey = alice.full,
        )
        val event2 = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/wrong",
                membership = Membership.JOIN,
            ),
            EventId("2"),
            alice,
            roomId,
            0L,
            stateKey = bob.full,
        )
        globalAccountDataStore.save(
            ClientEvent.GlobalAccountDataEvent(
                DirectEventContent(mappings = mapOf(alice to setOf(roomId)))
            )
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event1, event2)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/right"
    }

    @Test
    fun `avatarUrl » room is direct » when the avatar URL is explicitly set use it instead of member's avatar URL`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/123456") }
            roomStateStore.save(
                StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = alice.full,
                )
            )
            roomStateStore.save(
                StateEvent(
                    AvatarEventContent("mxc://localhost/123456"),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = "",
                )
            )
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    alice to setOf(
                        roomId,
                        RoomId("room2", "localhost")
                    )
                )
            )

            cut.updateRoomList(
                SyncEvents(
                    Sync.Response(""),
                    listOf(ClientEvent.GlobalAccountDataEvent(eventContent))
                )
            )

            roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }

    @Test
    fun `avatarUrl » room is direct » set the avatar URL to a member of a direct room when the new avatar URL is empty`() =
        runTest {
            roomStore.update(roomId) { Room(roomId, isDirect = true, avatarUrl = "mxc://localhost/abcdef") }
            globalAccountDataStore.save(
                ClientEvent.GlobalAccountDataEvent(
                    DirectEventContent(mappings = mapOf(bob to setOf(roomId, RoomId("room2", "localhost"))))
                )
            )
            roomStateStore.save(
                StateEvent(
                    MemberEventContent(
                        avatarUrl = "mxc://localhost/123456",
                        membership = Membership.JOIN
                    ),
                    EventId("1"),
                    bob,
                    roomId,
                    0L,
                    stateKey = bob.full
                )
            )
            val event = StateEvent(
                AvatarEventContent(""),
                EventId("1"),
                bob,
                roomId,
                0L,
                stateKey = bob.full,
            )

            cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

            roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }


    @Test
    fun `avatarUrl » room id not direct » do nothing on member event`() = runTest {
        roomStore.update(roomId) { Room(roomId) }
        val event = StateEvent(
            MemberEventContent(
                avatarUrl = "mxc://localhost/123456",
                membership = Membership.JOIN,
            ),
            EventId("1"),
            alice,
            roomId,
            0L,
            stateKey = alice.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe null
    }

    @Test
    fun `avatarUrl » room id not direct » set the avatar URL for normal rooms`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef") }
        val event = StateEvent(
            AvatarEventContent("mxc://localhost/123456"),
            EventId("1"),
            bob,
            roomId,
            0L,
            stateKey = bob.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe "mxc://localhost/123456"
    }

    @Test
    fun `avatarUrl » room id not direct » set an empty avatar URL for normal rooms`() = runTest {
        roomStore.update(roomId) { Room(roomId, avatarUrl = "mxc://localhost/abcdef") }
        val event = StateEvent(
            AvatarEventContent(""),
            EventId("1"),
            bob,
            roomId,
            0L,
            stateKey = bob.full,
        )

        cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(event)))

        roomStore.get(roomId).first()?.avatarUrl shouldBe null
    }

    // TODO this need to be completely rewritten as it is too complicated for the actual code that is tested

    @Test
    fun `displayName » non-existent NameEvent » existent CanonicalAliasEvent » set room name`() =
        `existent CanonicalAliasEvent » set room name`(false)

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 2 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 0 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 0 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 2 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes is 0 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes is 0 » set room name`(false)

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes is 0 » set room name to empty`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes is 0 » set room name to empty`(false)

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
            false
        )

    @Test
    fun `displayName » non-existent NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
            false
        )


    @Test
    fun `displayName » existent NameEvent » empty NameEvent » existent CanonicalAliasEvent » set room name`() =
        `existent CanonicalAliasEvent » set room name`(true)

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 2 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 0 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 0 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 2 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes is 0 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes is 0 » set room name`(true)

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes is 0 » set room name to empty`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes is 0 » set room name to empty`(true)

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 1 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
            true
        )

    @Test
    fun `displayName » existent NameEvent » empty NameEvent » non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 2 » set room name`() =
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
            true
        )


    @Test
    fun `displayName » existent NameEvent » with a non-empty name field » set room name`() = runTest {
        roomStateStore.save(nameEvent(1, user1, "The room name"))
        listOf(
            canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
            memberEvent(3, user1, "User1-Display", Membership.JOIN),
            memberEvent(4, user2, "User2-Display", Membership.INVITE),
            memberEvent(5, user3, "User3-Display", Membership.BAN),
            memberEvent(6, user4, "User4-Display", Membership.LEAVE)
        ).forEach { roomStateStore.save(it) }
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            explicitName = "The room name",
            summary = roomSummary
        )
    }

    @Test
    fun `displayName » existent NameEvent » set room name on invite no room summary`() = runTest {
        cut.calculateDisplayName(
            roomId,
            nameEventContent = NameEventContent("the room")
        ) shouldBe RoomDisplayName(
            explicitName = "the room",
            summary = null
        )
    }


    @Test
    fun `displayName » do nothing when room summary did not change at all`() = runTest {
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 2,
        )
        val roomBefore = simpleRoom.copy(name = RoomDisplayName(explicitName = "bla", summary = roomSummary))
        roomStore.update(roomId) { roomBefore }
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe null
    }


    @Test
    fun `deleteLeftRooms » forget rooms on leave when activated`() = runTest {
        cut.deleteLeftRooms(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = mapOf(roomId to Sync.Response.Rooms.LeftRoom())
                    )
                ),
                emptyList()
            )
        )

        forgetRooms shouldBe listOf(roomId)
    }

    @Test
    fun `deleteLeftRooms » not forget rooms on leave when disabled`() = runTest {
        config.deleteRoomsOnLeave = false
        roomStore.update(roomId) { simpleRoom.copy(membership = Membership.LEAVE) }

        roomStore.getAll().first { it.size == 1 }

        cut.deleteLeftRooms(
            SyncEvents(
                Sync.Response(
                    nextBatch = "",
                    room = Sync.Response.Rooms(
                        leave = mapOf(roomId to Sync.Response.Rooms.LeftRoom())
                    )
                ), emptyList()
            )
        )

        roomStore.get(roomId).first() shouldNotBe null
    }


    private fun memberEvent(
        i: Long,
        userId: UserId,
        displayName: String,
        membership: Membership
    ): StateEvent<MemberEventContent> {
        return StateEvent(
            MemberEventContent(
                displayName = displayName,
                membership = membership
            ),
            EventId("\$event$i"),
            userId,
            roomId,
            i,
            stateKey = userId.full
        )
    }

    private fun nameEvent(
        i: Long,
        userId: UserId,
        name: String
    ): StateEvent<NameEventContent> {
        return StateEvent(
            NameEventContent(name),
            EventId("\$event$i"),
            userId,
            roomId,
            i,
            stateKey = ""
        )
    }

    private fun canonicalAliasEvent(
        i: Long,
        userId: UserId, roomAliasId: RoomAliasId
    ): StateEvent<CanonicalAliasEventContent> {
        return StateEvent(
            CanonicalAliasEventContent(roomAliasId),
            EventId("\$event$i"),
            userId,
            roomId,
            1,
            stateKey = ""
        )
    }

    private suspend fun `existent CanonicalAliasEvent setup`() {
        listOf(
            canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
            memberEvent(3, user1, "User1-Display", Membership.JOIN),
            memberEvent(4, user2, "User2-Display", Membership.INVITE),
            memberEvent(5, user3, "User3-Display", Membership.BAN),
            memberEvent(6, user4, "User4-Display", Membership.LEAVE)
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 setup`() {
        listOf(
            memberEvent(3, user1, "User1-Display", Membership.JOIN),
            memberEvent(4, user2, "User2-Display", Membership.INVITE),
            memberEvent(7, user5, "User5-Display", Membership.BAN)
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 setup`() {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 setup`()

        listOf(
            memberEvent(5, user3, "User3-Display", Membership.LEAVE),
            memberEvent(6, user4, "User4-Display", Membership.LEAVE),
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 setup`() {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 setup`()

        listOf(
            memberEvent(5, user3, "User3-Display", Membership.JOIN),
            memberEvent(6, user4, "User4-Display", Membership.INVITE),
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited is 1 setup`() {
        listOf(
            memberEvent(3, user1, "User1-Display", Membership.JOIN),
            memberEvent(4, user2, "User2-Display", Membership.BAN),
            memberEvent(5, user3, "User3-Display", Membership.LEAVE),
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 setup`() {
        `non-existent CanonicalAliasEvent » joined plus invited is 1 setup`()
        listOf(
            memberEvent(6, user4, "User4-Display", Membership.LEAVE),
            memberEvent(7, user5, "User5-Display", Membership.LEAVE),
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited is 0 setup`() {
        listOf(
            memberEvent(3, user1, "User1-Display", Membership.LEAVE),
            memberEvent(4, user2, "User2-Display", Membership.BAN),
        ).forEach { roomStateStore.save(it) }
    }

    private suspend fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 setup`() {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 setup`()

        listOf(
            memberEvent(5, user3, "User3-Display", Membership.LEAVE),
            memberEvent(6, user4, "User4-Display", Membership.LEAVE),
            memberEvent(7, user5, "User5-Display", Membership.LEAVE),
        ).forEach { roomStateStore.save(it) }
    }

    private fun runTestWithNameEvent(
        empty: Boolean = false,
        block: suspend TestScope.() -> Unit
    ) = runTest {
        if (empty) {
            roomStateStore.save(nameEvent(1, user1, ""))
        }

        block()
    }

    private fun `existent CanonicalAliasEvent » set room name`(empty: Boolean) = runTestWithNameEvent(empty) {
        `existent CanonicalAliasEvent setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 1,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            explicitName = "#somewhere:localhost",
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1),
            joinedMemberCount = 1,
            invitedMemberCount = 1,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1),
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes greater equals joined plus invited minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 1,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1, user2),
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 0 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(),
            joinedMemberCount = 2,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(),
            otherUsersCount = 4,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1),
            joinedMemberCount = 2,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            otherUsersCount = 4,
            heroes = listOf(user1),
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited greater 1 » heroes less joined plus invited minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 2,
            invitedMemberCount = 2,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1, user2),
            otherUsersCount = 4,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes is 0 » set room name`(empty: Boolean) =
        runTestWithNameEvent(empty) {
            `non-existent CanonicalAliasEvent » joined plus invited is 1 setup`()
            val roomSummary = JoinedRoom.RoomSummary(
                heroes = listOf(),
                joinedMemberCount = 1,
                invitedMemberCount = 0,
            )
            cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
                isEmpty = true,
                summary = roomSummary
            )

        }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user2),
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user2),
            isEmpty = true,
            otherUsersCount = 0,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user2, user3),
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user2, user3),
            isEmpty = true,
            summary = roomSummary
        )
    }


    private fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user2),
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user2),
            isEmpty = true,
            otherUsersCount = 0,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 1 » heroes less left plus banned minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user2, user3),
            joinedMemberCount = 1,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user2, user3),
            isEmpty = true,
            otherUsersCount = 0,
            summary = roomSummary
        )

    }


    private fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes is 0 » set room name to empty`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(),
            joinedMemberCount = 0,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            isEmpty = true,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1),
            joinedMemberCount = 0,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1),
            isEmpty = true,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes greater equals left plus banned minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 setup`()
        roomStateStore.save(
            memberEvent(5, user3, "User3-Display", Membership.LEAVE),
        )
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 0,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1, user2),
            isEmpty = true,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 1 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1),
            joinedMemberCount = 0,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1),
            isEmpty = true,
            otherUsersCount = 0,
            summary = roomSummary
        )
    }

    private fun `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 » heroes is 2 » set room name`(
        empty: Boolean
    ) = runTestWithNameEvent(empty) {
        `non-existent CanonicalAliasEvent » joined plus invited is 0 » heroes less left plus banned minus 1 setup`()
        val roomSummary = JoinedRoom.RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 0,
            invitedMemberCount = 0,
        )
        cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
            heroes = listOf(user1, user2),
            isEmpty = true,
            otherUsersCount = 0,
            summary = roomSummary
        )
    }
}



