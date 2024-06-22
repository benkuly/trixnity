package net.folivo.trixnity.client.user

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryAccountStore
import net.folivo.trixnity.client.getInMemoryRoomUserStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class MemberEventHandlerTest : ShouldSpec({
    timeout = 5_000
    val roomId = RoomId("room", "localhost")
    lateinit var accountStore: AccountStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var scope: CoroutineScope

    lateinit var cut: UserMemberEventHandler

    val json = createMatrixEventJson()
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        accountStore = getInMemoryAccountStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        cut = UserMemberEventHandler(
            mockMatrixClientServerApiClient(json).first,
            accountStore,
            roomUserStore,
            UserInfo(UserId("alice", "server"), "a", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            TransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    context(UserMemberEventHandler::setRoomUser.name) {
        val user1 = UserId("user1", "server")
        val user2 = UserId("user2", "server")
        val user3 = UserId("user3", "server")
        val user4 = UserId("user4", "server")
        val user1Event = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event1"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user1.full
        )
        val user2Event = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event2"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user2.full
        )
        val user3Event = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event3"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user3.full
        )
        beforeTest {
            // This should be ignored
            roomUserStore.update(user4, roomId) {
                RoomUser(
                    roomId,
                    user4,
                    "U1 (@user4:server)",
                    StateEvent(
                        MemberEventContent(displayName = "U1", membership = Membership.BAN),
                        EventId("\$event4"),
                        UserId("sender", "server"),
                        roomId,
                        1234,
                        stateKey = user4.full
                    )
                )
            }
        }
        should("skip when user already present") {
            val event = user1Event.copy(
                content = MemberEventContent(
                    displayName = "U1",
                    membership = Membership.JOIN
                )
            )
            cut.setRoomUser(listOf(event))
            cut.setRoomUser(
                listOf(event.copy(content = event.content.copy(displayName = "CHANGED!!!"))),
                skipWhenAlreadyPresent = true
            )
            roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
        }
        context("user is new member") {
            should("set our displayName to 'DisplayName'") {
                val event = user1Event.copy(
                    content = MemberEventContent(
                        displayName = "U1",
                        membership = Membership.JOIN
                    )
                )
                cut.setRoomUser(listOf(event))
                roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
            }
        }
        context("user is member") {
            beforeTest {
                roomUserStore.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
                    )
                }
            }
            context("no other user has same displayName") {
                beforeTest {
                    roomUserStore.update(user2, roomId) {
                        RoomUser(
                            roomId,
                            user2,
                            "U2",
                            user2Event.copy(
                                content = MemberEventContent(
                                    displayName = "U2",
                                    membership = Membership.JOIN
                                )
                            )
                        )
                    }
                }
                should("set our displayName to 'DisplayName'") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "U1",
                            membership = Membership.JOIN
                        )
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
                }
                should("not change our displayName when it has not changed") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "OLD",
                            membership = Membership.JOIN
                        )
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "OLD", event)
                }
                should("set our displayName to '@user:server' when no displayName set") {
                    val event = user1Event.copy(content = MemberEventContent(membership = Membership.JOIN))
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId,
                        user1,
                        "@user1:server",
                        event
                    )
                }
                should("set our displayName to '@user:server' when displayName is empty") {
                    val event =
                        user1Event.copy(content = MemberEventContent(displayName = "", membership = Membership.JOIN))
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
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
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                }
                should("evaluate events from server multiple times and still be correct") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(event))
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                }
                should("find collision in same list") {
                    val user2EventJoin = user2Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    val user3EventJoin = user3Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(user2EventJoin, user3EventJoin))
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", user2EventJoin
                    )
                    roomUserStore.get(user3, roomId).first() shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", user3EventJoin
                    )
                }
            }
            context("two other users have same displayName") {
                should("set our displayName to 'DisplayName (@user:server)'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    roomUserStore.get(user3, roomId).first() shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
            context("one other user has same old displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "OLD", event2
                    )
                }
            }
            context("two other users have same old displayName") {
                should("keep displayName of the others'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
                    roomUserStore.update(user3, roomId) {
                        RoomUser(roomId, user3, "OLD (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
                    )
                    cut.setRoomUser(listOf(event))

                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "OLD (@user2:server)", event2
                    )
                    roomUserStore.get(user3, roomId).first() shouldBe RoomUser(
                        roomId, user3, "OLD (@user3:server)", event3
                    )
                }
            }
        }
        context("user is not member anymore") {
            beforeTest {
                roomUserStore.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
                    )
                }
            }
            context("one other user has same displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = Membership.BAN)
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId, user1, "U1", event
                    )
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1", event2
                    )
                }
            }
            context("two other users have same displayName") {
                should("keep displayName of the others") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
                    roomUserStore.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "U1",
                            membership = Membership.LEAVE
                        )
                    )
                    cut.setRoomUser(listOf(event))
                    roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    roomUserStore.get(user2, roomId).first() shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    roomUserStore.get(user3, roomId).first() shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
        }
    }
})