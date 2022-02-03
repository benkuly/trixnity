package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.model.sync.SyncResponse.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.RoomDisplayName
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings

class RoomServiceDisplayNameTest : ShouldSpec({
    val roomId = RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixApiClient>()
    lateinit var cut: RoomService
    val user1 = UserId("user1", "server")
    val user2 = UserId("user2", "server")
    val user3 = UserId("user3", "server")
    val user4 = UserId("user4", "server")
    val user5 = UserId("user5", "server")

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = RoomService(UserId("alice", "server"), store, api, mockk(), mockk(), mockk(), mockk())
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
        userId: UserId, roomAliasId: RoomAliasId
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

    context(RoomService::setRoomDisplayName.name) {
        beforeTest {
            store.room.update(roomId) { simpleRoom.copy(roomId = roomId) }
        }
        suspend fun ShouldSpecContainerScope.testWithoutNameFromNameEvent() {
            context("with an existent Canonical Alias Event") {
                should("set room name to the alias field value") {
                    listOf(
                        canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
                        memberEvent(3, user1, "User1-Display", JOIN),
                        memberEvent(4, user2, "User2-Display", INVITE),
                        memberEvent(5, user3, "User3-Display", BAN),
                        memberEvent(6, user4, "User4-Display", LEAVE)
                    ).forEach { store.roomState.update(it) }
                    val roomSummary = RoomSummary(
                        heroes = listOf(user1, user2),
                        joinedMemberCount = 1,
                        invitedMemberCount = 1,
                    )
                    cut.setRoomDisplayName(roomId, roomSummary)
                    store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                        explicitName = "#somewhere:localhost",
                        summary = roomSummary
                    )
                }
            }
            context("with a non-existent Canonical Alias Event") {
                context("|joined member| + |invited member| > 1") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", JOIN),
                            memberEvent(4, user2, "User2-Display", INVITE),
                            memberEvent(7, user5, "User5-Display", BAN)
                        ).forEach { store.roomState.update(it) }
                    }
                    context("|heroes| >= |joined member| + |invited member| - 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", LEAVE),
                                memberEvent(6, user4, "User4-Display", LEAVE),
                            ).forEach { store.roomState.update(it) }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 1,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1, user2),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 1,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    summary = roomSummary
                                )
                            }
                        }
                    }
                    context("|heroes| < |joined member| + |invited member| - 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", JOIN),
                                memberEvent(6, user4, "User4-Display", INVITE),
                            ).forEach { store.roomState.update(it) }
                        }
                        context("|heroes| = 0") {
                            should("set room name to the count of the invited and joined users") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(),
                                    joinedMemberCount = 2,
                                    invitedMemberCount = 2,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    otherUsersCount = 3,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero and a count of the remaining users") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1),
                                    joinedMemberCount = 2,
                                    invitedMemberCount = 2,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    otherUsersCount = 2,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1, user2),
                                    joinedMemberCount = 2,
                                    invitedMemberCount = 2,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    otherUsersCount = 1,
                                    summary = roomSummary
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 1") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", JOIN),
                            memberEvent(4, user2, "User2-Display", BAN),
                            memberEvent(5, user3, "User3-Display", LEAVE),
                        ).forEach { store.roomState.update(it) }
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(),
                                joinedMemberCount = 1,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, roomSummary)
                            store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                isEmpty = true,
                                summary = roomSummary
                            )
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user2),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    otherUsersCount = 1,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user2, user3),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    summary = roomSummary
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            listOf(
                                memberEvent(6, user4, "User4-Display", LEAVE),
                                memberEvent(7, user5, "User5-Display", LEAVE),
                            ).forEach { store.roomState.update(it) }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user2),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    otherUsersCount = 3,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user2, user3),
                                    joinedMemberCount = 1,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    otherUsersCount = 2,
                                    summary = roomSummary
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 0") {
                    beforeTest {
                        listOf(
                            memberEvent(3, user1, "User1-Display", LEAVE),
                            memberEvent(4, user2, "User2-Display", BAN),
                        ).forEach { store.roomState.update(it) }
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(),
                                joinedMemberCount = 0,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, roomSummary)
                            store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                isEmpty = true,
                                summary = roomSummary
                            )
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero, enclosed by an Empty Room String") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1),
                                    joinedMemberCount = 0,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und', enclosed by an Empty Room String ") {
                                store.roomState.update(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                )
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1, user2),
                                    joinedMemberCount = 0,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    summary = roomSummary
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            listOf(
                                memberEvent(5, user3, "User3-Display", LEAVE),
                                memberEvent(6, user4, "User4-Display", LEAVE),
                                memberEvent(7, user5, "User5-Display", LEAVE),
                            ).forEach { store.roomState.update(it) }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1),
                                    joinedMemberCount = 0,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    otherUsersCount = 3,
                                    summary = roomSummary
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val roomSummary = RoomSummary(
                                    heroes = listOf(user1, user2),
                                    joinedMemberCount = 0,
                                    invitedMemberCount = 0,
                                )
                                cut.setRoomDisplayName(roomId, roomSummary)
                                store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    otherUsersCount = 2,
                                    summary = roomSummary
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
                    listOf(
                        canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
                        memberEvent(3, user1, "User1-Display", JOIN),
                        memberEvent(4, user2, "User2-Display", INVITE),
                        memberEvent(5, user3, "User3-Display", BAN),
                        memberEvent(6, user4, "User4-Display", LEAVE)
                    ).forEach { store.roomState.update(it) }
                    val roomSummary = RoomSummary(
                        heroes = listOf(user1, user2),
                        joinedMemberCount = 1,
                        invitedMemberCount = 2,
                    )
                    cut.setRoomDisplayName(roomId, roomSummary)
                    store.room.get(roomId).value?.name shouldBe RoomDisplayName(
                        explicitName = "The room name",
                        summary = roomSummary
                    )
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