package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.room.RoomDisplayNameEventHandler.RoomDisplayNameChange
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomDisplayName
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.*
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomDisplayNameEventHandlerTest : ShouldSpec({
    timeout = 60_000
    val roomId = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var scope: CoroutineScope

    lateinit var cut: RoomDisplayNameEventHandler
    val user1 = UserId("user1", "server")
    val user2 = UserId("user2", "server")
    val user3 = UserId("user3", "server")
    val user4 = UserId("user4", "server")
    val user5 = UserId("user5", "server")

    val json = createMatrixEventJson()
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        cut = RoomDisplayNameEventHandler(
            mockMatrixClientServerApiClient(json).first,
            roomStore,
            roomStateStore,
        )
        roomStore.update(roomId) { simpleRoom.copy(roomId = roomId) }
    }

    afterTest {
        scope.cancel()
    }

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
                    memberEvent(3, user1, "User1-Display", JOIN),
                    memberEvent(4, user2, "User2-Display", INVITE),
                    memberEvent(5, user3, "User3-Display", BAN),
                    memberEvent(6, user4, "User4-Display", LEAVE)
                ).forEach { roomStateStore.save(it) }
                val roomSummary = RoomSummary(
                    heroes = listOf(user1, user2),
                    joinedMemberCount = 1,
                    invitedMemberCount = 1,
                )
                cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                    explicitName = "#somewhere:localhost",
                    summary = roomSummary
                )
            }
        }
        context("non-existent CanonicalAliasEvent") {
            context("joined plus invited greater 1") {
                beforeTest {
                    listOf(
                        memberEvent(3, user1, "User1-Display", JOIN),
                        memberEvent(4, user2, "User2-Display", INVITE),
                        memberEvent(7, user5, "User5-Display", BAN)
                    ).forEach { roomStateStore.save(it) }
                }
                context("heroes greater equals joined plus invited minus 1") {
                    beforeTest {
                        listOf(
                            memberEvent(5, user3, "User3-Display", LEAVE),
                            memberEvent(6, user4, "User4-Display", LEAVE),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1),
                                joinedMemberCount = 1,
                                invitedMemberCount = 1,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1, user2),
                                joinedMemberCount = 1,
                                invitedMemberCount = 1,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                summary = roomSummary
                            )
                        }
                    }
                }
                context("heroes less joined plus invited minus 1") {
                    beforeTest {
                        listOf(
                            memberEvent(5, user3, "User3-Display", JOIN),
                            memberEvent(6, user4, "User4-Display", INVITE),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 0") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(),
                                joinedMemberCount = 2,
                                invitedMemberCount = 2,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                otherUsersCount = 3,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1),
                                joinedMemberCount = 2,
                                invitedMemberCount = 2,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                otherUsersCount = 2,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1, user2),
                                joinedMemberCount = 2,
                                invitedMemberCount = 2,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                otherUsersCount = 1,
                                summary = roomSummary
                            )
                        }
                    }
                }
            }
            context("joined plus invited is 1") {
                beforeTest {
                    listOf(
                        memberEvent(3, user1, "User1-Display", JOIN),
                        memberEvent(4, user2, "User2-Display", BAN),
                        memberEvent(5, user3, "User3-Display", LEAVE),
                    ).forEach { roomStateStore.save(it) }
                }
                context("heroes is 0") {
                    should("set room name") {
                        val roomSummary = RoomSummary(
                            heroes = listOf(),
                            joinedMemberCount = 1,
                            invitedMemberCount = 0,
                        )
                        cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                        roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                            isEmpty = true,
                            summary = roomSummary
                        )
                    }
                }
                context("heroes greater equals left plus banned minus 1") {
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user2),
                                joinedMemberCount = 1,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                otherUsersCount = 1,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user2, user3),
                                joinedMemberCount = 1,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                summary = roomSummary
                            )
                        }
                    }
                }
                context("heroes less left plus banned minus 1") {
                    beforeTest {
                        listOf(
                            memberEvent(6, user4, "User4-Display", LEAVE),
                            memberEvent(7, user5, "User5-Display", LEAVE),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user2),
                                joinedMemberCount = 1,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                otherUsersCount = 3,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user2, user3),
                                joinedMemberCount = 1,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                otherUsersCount = 2,
                                summary = roomSummary
                            )
                        }
                    }
                }
            }
            context("joined plus invited is 0") {
                beforeTest {
                    listOf(
                        memberEvent(3, user1, "User1-Display", LEAVE),
                        memberEvent(4, user2, "User2-Display", BAN),
                    ).forEach { roomStateStore.save(it) }
                }
                context("heroes is 0") {
                    should("set room name to empty") {
                        val roomSummary = RoomSummary(
                            heroes = listOf(),
                            joinedMemberCount = 0,
                            invitedMemberCount = 0,
                        )
                        cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                        roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                            isEmpty = true,
                            summary = roomSummary
                        )
                    }
                }
                context("heroes greater equals left plus banned minus 1") {
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1),
                                joinedMemberCount = 0,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            roomStateStore.save(
                                memberEvent(5, user3, "User3-Display", LEAVE),
                            )
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1, user2),
                                joinedMemberCount = 0,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                summary = roomSummary
                            )
                        }
                    }
                }
                context("heroes less left plus banned minus 1") {
                    beforeTest {
                        listOf(
                            memberEvent(5, user3, "User3-Display", LEAVE),
                            memberEvent(6, user4, "User4-Display", LEAVE),
                            memberEvent(7, user5, "User5-Display", LEAVE),
                        ).forEach { roomStateStore.save(it) }
                    }
                    context("heroes is 1") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1),
                                joinedMemberCount = 0,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                                isEmpty = true,
                                otherUsersCount = 3,
                                summary = roomSummary
                            )
                        }
                    }
                    context("heroes is 2") {
                        should("set room name") {
                            val roomSummary = RoomSummary(
                                heroes = listOf(user1, user2),
                                joinedMemberCount = 0,
                                invitedMemberCount = 0,
                            )
                            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
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
    context("existent NameEvent") {
        context("with a non-empty name field") {
            beforeTest {
                roomStateStore.save(nameEvent(1, user1, "The room name"))
            }
            should("set room name") {
                listOf(
                    canonicalAliasEvent(2, user2, RoomAliasId("somewhere", "localhost")),
                    memberEvent(3, user1, "User1-Display", JOIN),
                    memberEvent(4, user2, "User2-Display", INVITE),
                    memberEvent(5, user3, "User3-Display", BAN),
                    memberEvent(6, user4, "User4-Display", LEAVE)
                ).forEach { roomStateStore.save(it) }
                val roomSummary = RoomSummary(
                    heroes = listOf(user1, user2),
                    joinedMemberCount = 1,
                    invitedMemberCount = 2,
                )
                cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
                roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
                    explicitName = "The room name",
                    summary = roomSummary
                )
            }
        }
        should("set room name on invite (no room summary)") {
            cut.setRoomDisplayName(roomId, RoomDisplayNameChange(nameEventContent = NameEventContent("the room")))
            roomStore.get(roomId).first().shouldNotBeNull().name shouldBe RoomDisplayName(
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
        val roomSummary = RoomSummary(
            heroes = listOf(user1, user2),
            joinedMemberCount = 1,
            invitedMemberCount = 2,
        )
        val roomBefore = simpleRoom.copy(name = RoomDisplayName(explicitName = "bla", summary = roomSummary))
        roomStore.update(roomId) { roomBefore }
        cut.setRoomDisplayName(roomId, RoomDisplayNameChange(roomSummary = roomSummary))
        roomStore.get(roomId).first() shouldBe roomBefore
    }
})