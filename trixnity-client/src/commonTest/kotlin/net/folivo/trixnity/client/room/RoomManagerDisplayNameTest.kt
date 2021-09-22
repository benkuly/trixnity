package net.folivo.trixnity.client.room

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerContext
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
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
    val room = MatrixId.RoomId("room", "server")
    val store = InMemoryStore()
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmManager>()
    val cut = RoomManager(store, api, olm, LoggerFactory.default)
    val user1 = UserId("user1", "server")
    val user2 = UserId("user2", "server")
    val user3 = UserId("user3", "server")
    val user4 = UserId("user4", "server")
    val user5 = UserId("user5", "server")

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
    }

    afterTest {
        clearMocks(api, olm)
        store.clear()
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
            room,
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
            room,
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
            room,
            1,
            stateKey = ""
        )
    }

    context(RoomManager::setRoomDisplayName.name) {
        beforeTest {
            store.rooms.update(room) { simpleRoom.copy(roomId = room) }
        }
        suspend fun ShouldSpecContainerContext.testWithoutNameFromNameEvent() {
            context("with an existent Canonical Alias Event") {
                should("set room name to the alias field value") {
                    val heroes = listOf(user1.toString(), user2.toString())
                    store.rooms.state.updateAll(
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
                        roomId = room
                    )
                    store.rooms.byId(room).value?.name shouldBe RoomDisplayName(explicitName = "#somewhere:localhost")
                }
            }
            context("with a non-existent Canonical Alias Event") {
                context("|joined member| + |invited member| > 1") {
                    beforeTest {
                        store.rooms.state.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", JOIN),
                                memberEvent(4, user2, "User2-Display", INVITE),
                                memberEvent(7, user5, "User5-Display", BAN)
                            )
                        )
                    }
                    context("|heroes| >= |joined member| + |invited member| - 1") {
                        beforeTest {
                            store.rooms.state.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val heroes = listOf(user1.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 1,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(heroesDisplayname = listOf("User1-Display"))
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user1.full, user2.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 1,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    heroesDisplayname = listOf(
                                        "User1-Display",
                                        "User2-Display"
                                    )
                                )
                            }
                        }
                    }
                    context("|heroes| < |joined member| + |invited member| - 1") {
                        beforeTest {
                            store.rooms.state.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", JOIN),
                                    memberEvent(6, user4, "User4-Display", INVITE),
                                )
                            )
                        }
                        context("|heroes| = 0") {
                            should("set room name to the count of the invited and joined users") {
                                val heroes = listOf<String>()
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(otherUsersCount = 3)
                            }
                        }
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero and a count of the remaining users") {
                                val heroes = listOf(user1.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    heroesDisplayname = listOf("User1-Display"),
                                    otherUsersCount = 2
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user1.full, user2.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 2,
                                    invitedMemberCountFromSync = 2,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    heroesDisplayname = listOf("User1-Display", "User2-Display"),
                                    otherUsersCount = 1
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 1") {
                    beforeTest {
                        store.rooms.state.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", JOIN),
                                memberEvent(4, user2, "User2-Display", BAN),
                                memberEvent(5, user3, "User3-Display", LEAVE),
                            )
                        )
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val heroes = listOf<String>()
                            cut.setRoomDisplayName(
                                heroes = heroes,
                                joinedMemberCountFromSync = 1,
                                invitedMemberCountFromSync = 0,
                                roomId = room
                            )
                            store.rooms.byId(room).value?.name shouldBe RoomDisplayName(isEmpty = true)
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero") {
                                val heroes = listOf(user2.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User2-Display"),
                                    otherUsersCount = 1
                                )

                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und'") {
                                val heroes = listOf(user2.full, user3.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User2-Display", "User3-Display")
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            store.rooms.state.updateAll(
                                listOf(
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                    memberEvent(7, user5, "User5-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user2.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User2-Display"),
                                    otherUsersCount = 3
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user2.full, user3.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 1,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User2-Display", "User3-Display"),
                                    otherUsersCount = 2
                                )
                            }
                        }
                    }
                }
                context("|joined member| + |invited member| = 0") {
                    beforeTest {
                        store.rooms.state.updateAll(
                            listOf(
                                memberEvent(3, user1, "User1-Display", LEAVE),
                                memberEvent(4, user2, "User2-Display", BAN),
                            )
                        )
                    }
                    context("|heroes| = 0") {
                        should("set room name to 'Leerer Raum'") {
                            val heroes = listOf<String>()
                            cut.setRoomDisplayName(
                                heroes = heroes,
                                joinedMemberCountFromSync = 0,
                                invitedMemberCountFromSync = 0,
                                roomId = room
                            )
                            store.rooms.byId(room).value?.name shouldBe RoomDisplayName(isEmpty = true)
                        }
                    }
                    context("|heroes| >= |left member| + |banned member| - 1") {
                        context("|heroes| = 1") {
                            should("set room name to the display name of the hero, enclosed by an Empty Room String") {
                                val heroes = listOf(user1.full)
                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User1-Display")
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the display names of the heroes concatenate with an 'und', enclosed by an Empty Room String ") {
                                val heroes = listOf(user1.full, user2.full)
                                store.rooms.state.update(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                )

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User1-Display", "User2-Display")
                                )
                            }
                        }
                    }
                    context("|heroes| < |left member| + |banned member| - 1") {
                        beforeTest {
                            store.rooms.state.updateAll(
                                listOf(
                                    memberEvent(5, user3, "User3-Display", LEAVE),
                                    memberEvent(6, user4, "User4-Display", LEAVE),
                                    memberEvent(7, user5, "User5-Display", LEAVE),
                                )
                            )
                        }
                        context("|heroes| = 1") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user1.full)

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User1-Display"),
                                    otherUsersCount = 3
                                )
                            }
                        }
                        context("|heroes| = 2") {
                            should("set room name to the concatenation of display names of the heroes and a count of the remaining users, enclosed by an Empty Room String") {
                                val heroes = listOf(user1.full, user2.full)

                                cut.setRoomDisplayName(
                                    heroes = heroes,
                                    joinedMemberCountFromSync = 0,
                                    invitedMemberCountFromSync = 0,
                                    roomId = room
                                )
                                store.rooms.byId(room).value?.name shouldBe RoomDisplayName(
                                    isEmpty = true,
                                    heroesDisplayname = listOf("User1-Display", "User2-Display"),
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
                    store.rooms.state.update(nameEvent(1, user1, "The room name"))
                }
                should("set room name to the name field value") {
                    val heroes = listOf(user1.toString(), user2.toString())
                    store.rooms.state.updateAll(
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
                        roomId = room
                    )
                    store.rooms.byId(room).value?.name shouldBe RoomDisplayName(explicitName = "The room name")
                }
            }
            context("with an empty name field") {
                beforeTest {
                    store.rooms.state.update(nameEvent(1, user1, ""))
                }
                testWithoutNameFromNameEvent()
            }
        }
        context("non-existent room name state event") {
            testWithoutNameFromNameEvent()
        }
    }

    context(RoomManager::getUserDisplayName.name) {
        context("with nonexistent userId") {
            should("return the UserId as String") {
                cut.getUserDisplayName(room, user1) shouldBe "@user1:server"
            }
        }
        context("with existent userId") {
            context("with nonexistent displayname value") {
                should("return the UserId as String") {
                    store.rooms.state.update(
                        Event.StateEvent(
                            MemberEventContent(membership = JOIN),
                            EventId("\$event1"),
                            user1,
                            room,
                            25,
                            stateKey = user1.full
                        )
                    )
                    cut.getUserDisplayName(room, user1) shouldBe "@user1:server"
                }
            }
            context("with existent displayname value") {
                beforeTest {
                    store.rooms.state.updateAll(
                        listOf(
                            memberEvent(1, user1, "User1-Display", JOIN),
                            memberEvent(2, user2, "User2-Display", INVITE),
                            memberEvent(3, user3, "User3-Display", JOIN),
                            memberEvent(4, user4, "User4-Display", JOIN),
                        )
                    )
                }
                context("with unique displayname value") {
                    should("return the value of the displayname field") {
                        cut.getUserDisplayName(room, user1) shouldBe "User1-Display"
                    }
                }
                context("with non-unique displayname value") {
                    should("return the value of the displayname field concatenated with the userId") {
                        store.rooms.state.update(
                            memberEvent(5, user5, "User1-Display", JOIN)
                        )
                        cut.getUserDisplayName(
                            room, user1
                        ) shouldBe "User1-Display (@user1:server)"
                    }
                }
            }
        }
    }
})