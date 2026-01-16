package de.connect2x.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.getInMemoryAccountStore
import de.connect2x.trixnity.client.getInMemoryRoomUserStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

class MemberEventHandlerTest : TrixnityBaseTest() {

    private val roomId = RoomId("!room:localhost")
    private val user1 = UserId("user1", "server")
    private val user2 = UserId("user2", "server")
    private val user3 = UserId("user3", "server")
    private val user4 = UserId("user4", "server")
    private val user1Event = StateEvent(
        MemberEventContent(membership = Membership.JOIN),
        EventId("\$event1"),
        UserId("sender", "server"),
        roomId,
        1234,
        stateKey = user1.full
    )
    private val user2Event = StateEvent(
        MemberEventContent(membership = Membership.JOIN),
        EventId("\$event2"),
        UserId("sender", "server"),
        roomId,
        1234,
        stateKey = user2.full
    )
    private val user3Event = StateEvent(
        MemberEventContent(membership = Membership.JOIN),
        EventId("\$event3"),
        UserId("sender", "server"),
        roomId,
        1234,
        stateKey = user3.full
    )

    private val roomUserStore = getInMemoryRoomUserStore {
        update(user4, roomId) {
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

    private val cut = UserMemberEventHandler(
        mockMatrixClientServerApiClient(),
        getInMemoryAccountStore(),
        roomUserStore,
        UserInfo(UserId("alice", "server"), "a", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        TransactionManagerMock(),
    )

    @Test
    fun `setRoomUser » skip when user already present`() = runTest {
        val event = user1Event.copy(
            content = MemberEventContent(
                displayName = "U1",
                membership = Membership.JOIN
            )
        )
        cut.apply {
            setRoomUser(listOf(event))
            setRoomUser(
                listOf(event.copy(content = event.content.copy(displayName = "CHANGED!!!"))),
                skipWhenAlreadyPresent = true
            )
        }
        roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
    }

    @Test
    fun `setRoomUser » user is new member » set our displayName to 'DisplayName'`() = runTest {
        val event = user1Event.copy(
            content = MemberEventContent(
                displayName = "U1",
                membership = Membership.JOIN
            )
        )
        cut.setRoomUser(listOf(event))
        roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
    }

    @Test
    fun `setRoomUser » user is member » no other user has same displayName » set our displayName to 'DisplayName'`() =
        runTest {
            setupNoOtherUserHasSameDisplayName()
            val event = user1Event.copy(
                content = MemberEventContent(
                    displayName = "U1",
                    membership = Membership.JOIN
                )
            )
            cut.setRoomUser(listOf(event))
            roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "U1", event)
        }

    @Test
    fun `setRoomUser » user is member » no other user has same displayName » not change our displayName when it has not changed`() =
        runTest {
            setupNoOtherUserHasSameDisplayName()
            val event = user1Event.copy(
                content = MemberEventContent(
                    displayName = "OLD",
                    membership = Membership.JOIN
                )
            )
            cut.setRoomUser(listOf(event))
            roomUserStore.get(user1, roomId).first() shouldBe RoomUser(roomId, user1, "OLD", event)
        }

    @Test
    fun `setRoomUser » user is member » no other user has same displayName » set our displayName to user server when no displayName set`() =
        runTest {
            setupNoOtherUserHasSameDisplayName()
            val event = user1Event.copy(content = MemberEventContent(membership = Membership.JOIN))
            cut.setRoomUser(listOf(event))
            roomUserStore.get(user1, roomId).first() shouldBe RoomUser(
                roomId,
                user1,
                "@user1:server",
                event
            )
        }

    @Test
    fun `setRoomUser » user is member » no other user has same displayName » set our displayName to user server when displayName is empty`() =
        runTest {
            setupNoOtherUserHasSameDisplayName()
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

    @Test
    fun `setRoomUser » user is member » one other user has same displayName » set displayName of the other and us to 'DisplayName user1 server'`() =
        runTest {
            setupUserIsMember()
            val event2 =
                user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
            roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
            val event = user1Event.copy(
                content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
            )
            cut.setRoomUser(listOf(event))
            roomUserStore.apply {
                get(user1, roomId).first() shouldBe RoomUser(
                    roomId, user1, "U1 (@user1:server)", event
                )
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1 (@user2:server)", event2
                )
            }
        }

    @Test
    fun `setRoomUser » user is member » one other user has same displayName » evaluate events from server multiple times and still be correct`() =
        runTest {
            setupUserIsMember()
            val event2 =
                user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
            roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
            val event = user1Event.copy(
                content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
            )
            cut.apply {
                setRoomUser(listOf(event))
                setRoomUser(listOf(event))
            }
            roomUserStore.apply {
                get(user1, roomId).first() shouldBe RoomUser(
                    roomId, user1, "U1 (@user1:server)", event
                )
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1 (@user2:server)", event2
                )
            }
        }

    @Test
    fun `setRoomUser » user is member » one other user has same displayName » find collision in same list`() =
        runTest {
            setupUserIsMember()
            val user2EventJoin = user2Event.copy(
                content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
            )
            val user3EventJoin = user3Event.copy(
                content = MemberEventContent(displayName = "U1", membership = Membership.JOIN)
            )
            cut.setRoomUser(listOf(user2EventJoin, user3EventJoin))
            roomUserStore.apply {
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1 (@user2:server)", user2EventJoin
                )
                get(user3, roomId).first() shouldBe RoomUser(
                    roomId, user3, "U1 (@user3:server)", user3EventJoin
                )
            }
        }

    @Test
    fun `setRoomUser » user is member » two other users have same displayName » set our displayName to 'DisplayName user server'`() =
        runTest {
            setupUserIsMember()
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
            roomUserStore.apply {
                get(user1, roomId).first() shouldBe RoomUser(
                    roomId, user1, "U1 (@user1:server)", event
                )
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1 (@user2:server)", event2
                )
                get(user3, roomId).first() shouldBe RoomUser(
                    roomId, user3, "U1 (@user3:server)", event3
                )
            }
        }

    @Test
    fun `setRoomUser » user is member » one other user has same old displayName » set displayName of the other to 'DisplayName'`() =
        runTest {
            setupUserIsMember()
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

    @Test
    fun `setRoomUser » user is member » two other users have same old displayName » keep displayName of the others'`() =
        runTest {
            setupUserIsMember()
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

            roomUserStore.apply {
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "OLD (@user2:server)", event2
                )
                get(user3, roomId).first() shouldBe RoomUser(
                    roomId, user3, "OLD (@user3:server)", event3
                )
            }
        }

    @Test
    fun `setRoomUser » user is not member anymore » one other user has same displayName » set displayName of the other to 'DisplayName'`() =
        runTest {
            setupUserIsNotMemberAnymore()
            val event2 =
                user2Event.copy(content = MemberEventContent(displayName = "U1", membership = Membership.JOIN))
            roomUserStore.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
            val event = user1Event.copy(
                content = MemberEventContent(displayName = "U1", membership = Membership.BAN)
            )
            cut.setRoomUser(listOf(event))
            roomUserStore.apply {
                get(user1, roomId).first() shouldBe RoomUser(
                    roomId, user1, "U1", event
                )
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1", event2
                )
            }
        }

    @Test
    fun `setRoomUser » user is not member anymore » two other users have same displayName » keep displayName of the others`() =
        runTest {
            setupUserIsNotMemberAnymore()
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
            roomUserStore.apply {
                get(user1, roomId).first() shouldBe RoomUser(
                    roomId, user1, "U1 (@user1:server)", event
                )
                get(user2, roomId).first() shouldBe RoomUser(
                    roomId, user2, "U1 (@user2:server)", event2
                )
                get(user3, roomId).first() shouldBe RoomUser(
                    roomId, user3, "U1 (@user3:server)", event3
                )
            }
        }

    private suspend fun setupNoOtherUserHasSameDisplayName() {
        setupUserIsMember()
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

    private suspend fun setupUserIsMember() {
        roomUserStore.update(user1, roomId) {
            RoomUser(
                roomId,
                user1,
                "OLD",
                user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
            )
        }
    }

    private suspend fun setupUserIsNotMemberAnymore() {
        roomUserStore.update(user1, roomId) {
            RoomUser(
                roomId,
                user1,
                "OLD",
                user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = Membership.JOIN))
            )
        }
    }
}