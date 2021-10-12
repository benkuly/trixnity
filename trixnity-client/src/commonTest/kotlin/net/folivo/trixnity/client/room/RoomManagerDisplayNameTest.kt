package net.folivo.trixnity.client.room

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerContext
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomDisplayName
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.testutils.createInMemoryStore
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalKotest::class)
class RoomManagerDisplayNameTest : ShouldSpec({
    val roomId = MatrixId.RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixApiClient>()
    lateinit var cut: RoomManager
    val user1 = UserId("user1", "server")
    val user2 = UserId("user2", "server")
    val user3 = UserId("user3", "server")
    val user4 = UserId("user4", "server")
    val user5 = UserId("user5", "server")

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
        storeScope = CoroutineScope(Dispatchers.Default)
        store = createInMemoryStore(storeScope).apply { init() }
        cut = RoomManager(store, api, mockk(), mockk(), loggerFactory = LoggerFactory.default)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    fun memberEvent(
        i: Long,
        userId: UserId,
        displayName: String,
        membership: MemberEventContent.Membership
    ): Event.StateEvent<MemberEventContent> {
        return Event.StateEvent(
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
    ): Event.StateEvent<NameEventContent> {
        return Event.StateEvent(
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
        userId: UserId, roomAliasId: MatrixId.RoomAliasId
    ): Event.StateEvent<CanonicalAliasEventContent> {
        return Event.StateEvent(
            CanonicalAliasEventContent(roomAliasId),
            EventId("\$event$i"),
            userId,
            roomId,
            1,
            stateKey = ""
        )
    }

    context(RoomManager::setRoomUser.name) {
        val user1Event = Event.StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event1"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user1.full
        )
        val user2Event = Event.StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event2"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user2.full
        )
        val user3Event = Event.StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event3"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user3.full
        )
        beforeTest {
            // This should be ignored
            store.roomUser.update(user4, roomId) {
                RoomUser(
                    roomId,
                    user4,
                    "U1 (@user4:server)",
                    Event.StateEvent(
                        MemberEventContent(displayName = "U1", membership = BAN),
                        EventId("\$event4"),
                        UserId("sender", "server"),
                        roomId,
                        1234,
                        stateKey = user4.full
                    )
                )
            }
        }
        context("user is new member") {
            should("set our displayName to 'DisplayName'") {
                val event = user1Event.copy(
                    content = MemberEventContent(
                        displayName = "U1",
                        membership = JOIN
                    )
                )
                cut.setRoomUser(event)
                store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "U1", event)
            }
        }
        context("user is member") {
            beforeTest {
                store.roomUser.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    )
                }
            }
            context("no other user has same displayName") {
                beforeTest {
                    store.roomUser.update(user2, roomId) {
                        RoomUser(
                            roomId,
                            user2,
                            "U2",
                            user2Event.copy(content = MemberEventContent(displayName = "U2", membership = JOIN))
                        )
                    }
                }
                should("set our displayName to 'DisplayName'") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "U1",
                            membership = JOIN
                        )
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "U1", event)
                }
                should("not change our displayName when it has not changed") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "OLD",
                            membership = JOIN
                        )
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "OLD", event)
                }
                should("set our displayName to '@user:server' when no displayName set") {
                    val event = user1Event.copy(content = MemberEventContent(membership = JOIN))
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId,
                        user1,
                        "@user1:server",
                        event
                    )
                }
                should("set our displayName to '@user:server' when displayName is empty") {
                    val event = user1Event.copy(content = MemberEventContent(displayName = "", membership = JOIN))
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId,
                        user1,
                        "@user1:server",
                        event
                    )
                }
            }
            context("one other user has same displayName") {
                should("set displayName of the other and us to 'DisplayName (@user1:server)'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                }
            }
            context("two other users have same displayName") {
                should("set our displayName to 'DisplayName (@user:server)'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
            context("one other user has same old displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "OLD", event2
                    )
                }
            }
            context("two other users have same old displayName") {
                should("keep displayName of the others'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "OLD (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)

                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "OLD (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "OLD (@user3:server)", event3
                    )
                }
            }
        }
        context("user is not member anymore") {
            beforeTest {
                store.roomUser.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    )
                }
            }
            should("set our displayName to 'DisplayName (@user:server)'") {
                val event = user1Event.copy(
                    content = MemberEventContent(displayName = "OLD", membership = LEAVE)
                )
                cut.setRoomUser(event)
                store.roomUser.get(user1, roomId) shouldBe RoomUser(
                    roomId, user1, "OLD (@user1:server)", event
                )
            }
            context("one other user has same displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = BAN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1", event2
                    )
                }
            }
            context("two other users have same displayName") {
                should("keep displayName of the others") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = LEAVE)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
        }
    }

    context(RoomManager::setRoomDisplayName.name) {
        beforeTest {
            store.room.update(roomId) { simpleRoom.copy(roomId = roomId) }
        }
        suspend fun ShouldSpecContainerContext.testWithoutNameFromNameEvent() {
            context("with an existent Canonical Alias Event") {
                should("set room name to the alias field value") {
                    val heroes = listOf(user1, user2)
                    store.roomState.updateAll(
                        listOf(
                            canonicalAliasEvent(2, user2, MatrixId.RoomAliasId("somewhere", "localhost")),
                            memberEvent(3, user1, "User1-Display", JOIN),
                            memberEvent(4, user2, "User2-Display", INVITE),
                            memberEvent(5, user3, "User3-Display", BAN),
                            memberEvent(6, user4, "User4-Display", LEAVE)
                        )
                    )
                    cut.setRoomDisplayName(
                        heroes = heroes,
                        joinedMemberCountFromSync = 1,
                        invitedMemberCountFromSync = 1,
                        roomId = roomId
                    )
                    store.room.get(roomId).value?.name shouldBe RoomDisplayName(explicitName = "#somewhere:localhost")
                }
            }
            context("with a non-existent Canonical Alias Event") {
                context("|joined member| + |invited member| > 1") {
                    beforeTest {
                        store.roomState.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", JOIN),
                                memberEvent(4, user2, "User2-Display", INVITE),
                                memberEvent(7, user5, "User5-Display", BAN)
                            )
                        )
                    }
                    context("|heroes| >= |joined member| + |invited member| - 1") {
                        beforeTest {
                            store.roomState.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val heroes = listOf(user1)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 1,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    heroes = listOf(user1)
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user1, user2)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 1,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    heroes = listOf(user1, user2)
                                )
                            }
                        }
                    }
                    context("|heroes| < |joined member| + |invited member| - 1") {
                        beforeTest {
                            store.roomState.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", JOIN),
                                    memberEvent(6, user4, "User4-Display", INVITE),
                                )
                            )
                        }
                        context("|heroes| = 0") {
                            should("set room name to the count of the invited and joined users") {
                                val heroes = listOf<UserId>()
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(otherUsersCount = 3)
                            }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero and a count of the remaining users") {
                                val heroes = listOf(user1)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    heroes = listOf(user1),
                                    otherUsersCount = 2
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user1, user2)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    heroes = listOf(user1, user2),
                                    otherUsersCount = 1
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 1") {
                    beforeTest {
                        store.roomState.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", JOIN),
                                memberEvent(4, user2, "User2-Display", BAN),
                                memberEvent(5, user3, "User3-Display", LEAVE),
                            )
                        )
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val heroes = listOf<UserId>()
                            cut.setRoomDisplayName(
                                heroes = heroes,
                                joinedMemberCountFromSync = 1,
                                invitedMemberCountFromSync = 0,
                                roomId = roomId
                            )
                            store.room.get(roomId).value?.name shouldBe RoomDisplayName(isEmpty = true)
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val heroes = listOf(user2)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user2),
                                    otherUsersCount = 1
                                )

                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user2, user3)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user2, user3)
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            store.roomState.updateAll(
                                listOf(
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                    memberEvent(7, user5, "User5-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user2)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user2),
                                    otherUsersCount = 3
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user2, user3)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user2, user3),
                                    otherUsersCount = 2
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 0") {
                    beforeTest {
                        store.roomState.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", LEAVE),
                                memberEvent(4, user2, "User2-Display", BAN),
                            )
                        )
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val heroes = listOf<UserId>()
                            cut.setRoomDisplayName(
                                heroes = heroes,
                                joinedMemberCountFromSync = 0,
                                invitedMemberCountFromSync = 0,
                                roomId = roomId
                            )
                            store.room.get(roomId).value?.name shouldBe RoomDisplayName(isEmpty = true)
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero, enclosed by an Empty Room String") {
                                val heroes = listOf(user1)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user1)
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und', enclosed by an Empty Room String ") {
                                val heroes = listOf(user1, user2)
                                store.roomState.update(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                )

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user1, user2)
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            store.roomState.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                    memberEvent(7, user5, "User5-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user1)

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user1),
                                    otherUsersCount = 3
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user1, user2)

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = roomId
                                )
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroes = listOf(user1, user2),
                                    otherUsersCount = 2
                                )
                            }
                        }
                    }
                }
            }
        }
        context("existent room name state event") {
            context("with a non-empty name field") {
                beforeTest {
                    store.roomState.update(nameEvent(1, user1, "The room name"))
                }
                should("set room name to the name field value") {
                    val heroes = listOf(user1, user2)
                    store.roomState.updateAll(
                        listOf(
                            canonicalAliasEvent(2, user2, MatrixId.RoomAliasId("somewhere", "localhost")),
                            memberEvent(3, user1, "User1-Display", JOIN),
                            memberEvent(4, user2, "User2-Display", INVITE),
                            memberEvent(5, user3, "User3-Display", BAN),
                            memberEvent(6, user4, "User4-Display", LEAVE)
                        )
                    )
                    cut.setRoomDisplayName(
                        heroes = heroes,
                        joinedMemberCountFromSync = 1,
                        invitedMemberCountFromSync = 1,
                        roomId = roomId
                    )
                    store.room.get(roomId).value?.name shouldBe RoomDisplayName(explicitName = "The room name")
                }
            }
            context("with an empty name field") {
                beforeTest {
                    store.roomState.update(nameEvent(1, user1, ""))
                }
                testWithoutNameFromNameEvent()
            }
        }
        context("non-existent room name state event") {
            testWithoutNameFromNameEvent()
        }
    }
})