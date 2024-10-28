package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.LeftRoom
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
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomListHandlerTest : ShouldSpec({
    timeout = 10_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val roomId = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var roomAccountDataStore: RoomAccountDataStore

    lateinit var config: MatrixClientConfiguration
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    val forgetRooms = mutableListOf<RoomId>()

    lateinit var cut: RoomListHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        config = MatrixClientConfiguration()
        val (api, _) = mockMatrixClientServerApiClient(json)
        forgetRooms.clear()
        cut = RoomListHandler(
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
    }

    afterTest {
        scope.cancel()
    }

    context(RoomListHandler::updateRoomList.name) {
        context("unreadMessageCount") {
            should("set unread message count") {
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
        }
        context("markedUnread") {
            should("not mark as unread when not set") {
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
            should("mark as unread when set") {
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
        }
        context("lastRelevantEventId") {
            should("setlastRelevantEventId ") {
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
        }
    }
    context("isDirect") {
        should("set the room to direct == 'true' when a DirectEventContent is found for the room") {
            roomStore.update(roomId) { Room(roomId, isDirect = false) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), roomId)
                )
            )
            roomStore.getAll().first { it.size == 1 }

            cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(ClientEvent.GlobalAccountDataEvent(eventContent))))

            roomStore.get(roomId).first()?.isDirect shouldBe true
        }
        should("set the room to direct == 'false' when no DirectEventContent is found for the room") {
            val room1 = RoomId("room1", "localhost")
            val room2 = RoomId("room2", "localhost")
            roomStore.update(room1) { Room(room1, isDirect = true) }
            roomStore.update(room2) { Room(room2, isDirect = true) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(room2)
                )
            )
            roomStore.getAll().first { it.size == 2 }

            cut.updateRoomList(SyncEvents(Sync.Response(""), listOf(ClientEvent.GlobalAccountDataEvent(eventContent))))

            roomStore.get(room1).first()?.isDirect shouldBe false
            roomStore.get(room2).first()?.isDirect shouldBe true
        }
    }
    context("avatarUrl") {
        context("room is direct") {
            should("set the avatar URL to a member's avatar URL") {
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
            should("update the room's avatar URL") {
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
            should("when the avatar URL is explicitly set use it instead of member's avatar URL") {
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
            should("set the avatar URL to a member of a direct room when the new avatar URL is empty") {
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
        }
        context("room id not direct") {
            should("do nothing on member event") {
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
            should("set the avatar URL for normal rooms") {
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

            should("set an empty avatar URL for normal rooms") {
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
        }
    }
    // TODO this need to be completely rewritten as it is too complicated for the actual code that is tested
    context("displayName") {
        val user1 = UserId("user1", "server")
        val user2 = UserId("user2", "server")
        val user3 = UserId("user3", "server")
        val user4 = UserId("user4", "server")
        val user5 = UserId("user5", "server")
        fun memberEvent(
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

        fun nameEvent(
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

        fun canonicalAliasEvent(
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

        suspend fun ShouldSpecContainerScope.testWithoutNameFromNameEvent() {
            context("existent CanonicalAliasEvent") {
                should("set room name") {
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
                        invitedMemberCount = 1,
                    )
                    cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe RoomDisplayName(
                        explicitName = "#somewhere:localhost",
                        summary = roomSummary
                    )
                }
            }
            context("non-existent CanonicalAliasEvent") {
                context("joined plus invited greater 1") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", Membership.JOIN),
                            memberEvent(4, user2, "User2-Display", Membership.INVITE),
                            memberEvent(7, user5, "User5-Display", Membership.BAN)
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes greater equals joined plus invited minus 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", Membership.LEAVE),
                                memberEvent(6, user4, "User4-Display", Membership.LEAVE),
                            ).forEach { roomStateStore.save(it) }
                        }
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                        }
                    }
                    context("heroes less joined plus invited minus 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", Membership.JOIN),
                                memberEvent(6, user4, "User4-Display", Membership.INVITE),
                            ).forEach { roomStateStore.save(it) }
                        }
                        context("heroes is 0") {
                            should("set room name") {
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
                        }
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                        }
                    }
                }
                context("joined plus invited is 1") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", Membership.JOIN),
                            memberEvent(4, user2, "User2-Display", Membership.BAN),
                            memberEvent(5, user3, "User3-Display", Membership.LEAVE),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 0") {
                        should("set room name") {
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
                    }
                    context("heroes greater equals left plus banned minus 1") {
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                        }
                    }
                    context("heroes less left plus banned minus 1") {
                        beforeTest {
                            listOf(
                                memberEvent(6, user4, "User4-Display", Membership.LEAVE),
                                memberEvent(7, user5, "User5-Display", Membership.LEAVE),
                            ).forEach { roomStateStore.save(it) }
                        }
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                        }
                    }
                }
                context("joined plus invited is 0") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", Membership.LEAVE),
                            memberEvent(4, user2, "User2-Display", Membership.BAN),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 0") {
                        should("set room name to empty") {
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
                    }
                    context("heroes greater equals left plus banned minus 1") {
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                        }
                    }
                    context("heroes less left plus banned minus 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", Membership.LEAVE),
                                memberEvent(6, user4, "User4-Display", Membership.LEAVE),
                                memberEvent(7, user5, "User5-Display", Membership.LEAVE),
                            ).forEach { roomStateStore.save(it) }
                        }
                        context("heroes is 1") {
                            should("set room name") {
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
                        }
                        context("heroes is 2") {
                            should("set room name") {
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
                    }
                }
            }
        }
        context("existent NameEvent") {
            context("with a non-empty name field") {
                beforeTest {
                    roomStateStore.save(nameEvent(1, user1, "The room name"))
                }
                should("set room name") {
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
            }
            should("set room name on invite (no room summary)") {
                cut.calculateDisplayName(
                    roomId,
                    nameEventContent = NameEventContent("the room")
                ) shouldBe RoomDisplayName(
                    explicitName = "the room",
                    summary = null
                )
            }
            context("empty NameEvent") {
                beforeTest {
                    roomStateStore.save(nameEvent(1, user1, ""))
                }
                testWithoutNameFromNameEvent()
            }
        }
        context("non-existent NameEvent") {
            testWithoutNameFromNameEvent()
        }
        should("do nothing, when room summary did not change at all") {
            val roomSummary = JoinedRoom.RoomSummary(
                heroes = listOf(user1, user2),
                joinedMemberCount = 1,
                invitedMemberCount = 2,
            )
            val roomBefore = simpleRoom.copy(name = RoomDisplayName(explicitName = "bla", summary = roomSummary))
            roomStore.update(roomId) { roomBefore }
            cut.calculateDisplayName(roomId, roomSummary = roomSummary) shouldBe null
        }
    }
    context(RoomListHandler::deleteLeftRooms.name) {
        should("forget rooms on leave when activated") {
            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = mapOf(roomId to LeftRoom())
                        )
                    ),
                    emptyList()
                )
            )

            forgetRooms shouldBe listOf(roomId)
        }
        should("not forget rooms on leave when disabled") {
            config.deleteRoomsOnLeave = false
            roomStore.update(roomId) { simpleRoom.copy(membership = Membership.LEAVE) }

            roomStore.getAll().first { it.size == 1 }

            cut.deleteLeftRooms(
                SyncEvents(
                    Sync.Response(
                        nextBatch = "",
                        room = Sync.Response.Rooms(
                            leave = mapOf(roomId to LeftRoom())
                        )
                    ), emptyList()
                )
            )

            roomStore.get(roomId).first() shouldNotBe null
        }
    }
})