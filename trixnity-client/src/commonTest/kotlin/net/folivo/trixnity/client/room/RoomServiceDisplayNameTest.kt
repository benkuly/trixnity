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
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalKotest::class)
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
        cut = RoomService(store, api, mockk(), mockk(), mockk(), loggerFactory = LoggerFactory.default)
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
        suspend fun ShouldSpecContainerContext.testWithoutNameFromNameEvent() {
            context("with an existent Canonical Alias Event") {
                should("set room name to the alias field value") {
                    val heroes = listOf(user1, user2)
                    store.roomState.updateAll(
                        listOf(
                            canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
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
                            canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
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