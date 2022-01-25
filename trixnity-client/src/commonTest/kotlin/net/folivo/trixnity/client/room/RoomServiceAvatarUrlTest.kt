package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

class RoomServiceAvatarUrlTest : ShouldSpec({
    timeout = 5_000
    val room = RoomId("room", "localhost")
    val bob = UserId("bob", "localhost")
    val alice = UserId("alice", "localhost")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var scope: CoroutineScope
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmService>()
    val key = mockk<KeyService>()
    val users = mockk<UserService>(relaxUnitFun = true)
    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = RoomService(store, api, olm, key, users, mockk())
    }

    afterTest {
        clearAllMocks()
        scope.cancel()
        storeScope.cancel()
    }

    context(RoomService::setAvatarUrlForDirectRooms.name) {
        should("set the avatar URL to a member's avatar URL") {
            store.room.update(room) { Room(room, avatarUrl = null) }
            store.roomState.update(
                Event.StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = MemberEventContent.Membership.JOIN),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = alice.full,
                )
            )
            val event = Event.GlobalAccountDataEvent(
                DirectEventContent(
                    mappings = mapOf(
                        alice to setOf(
                            room,
                            RoomId("room2", "localhost")
                        )
                    )
                )
            )

            cut.setAvatarUrlForDirectRooms(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/abcdef"
        }

        should("when the avatar URL is explicitly set use it instead of member's avatar URL") {
            store.room.update(room) { Room(room, avatarUrl = "mxc://localhost/123456") }
            store.roomState.update(
                Event.StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = MemberEventContent.Membership.JOIN),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = alice.full,
                )
            )
            store.roomState.update(
                Event.StateEvent(
                    AvatarEventContent("mxc://localhost/123456"),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = "",
                )
            )
            val event = Event.GlobalAccountDataEvent(
                DirectEventContent(
                    mappings = mapOf(
                        alice to setOf(
                            room,
                            RoomId("room2", "localhost")
                        )
                    )
                )
            )

            cut.setAvatarUrlForDirectRooms(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/123456"
        }
    }

    context(RoomService::setAvatarUrlForMemberUpdates.name) {
        should("update the room's avatar URL when the room is a direct room") {
            store.account.userId.value = alice
            store.room.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                MemberEventContent(
                    avatarUrl = "mxc://localhost/123456",
                    membership = MemberEventContent.Membership.JOIN,
                ),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("do nothing when the room is not a direct room") {
            store.account.userId.value = alice
            store.room.update(room) { Room(room, isDirect = false) }
            val event = Event.StateEvent(
                MemberEventContent(
                    avatarUrl = "mxc://localhost/123456",
                    membership = MemberEventContent.Membership.JOIN,
                ),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            store.room.get(room).value?.avatarUrl shouldBe null
        }

        should("use the membership event of other user and not own (which is the invitation we might have sent)") {
            store.account.userId.value = alice
            store.room.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                // invitation
                MemberEventContent(
                    avatarUrl = "mxc://localhost/abcdef",
                    membership = MemberEventContent.Membership.JOIN,
                ),
                EventId("1"),
                alice,
                room,
                0L,
                stateKey = alice.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            store.room.get(room).value?.avatarUrl shouldBe null
        }
    }

    context(RoomService::setAvatarUrlForAvatarEvents.name) {
        should("set the avatar URL for normal rooms") {
            store.room.update(room) { Room(room, avatarUrl = "mxc://localhost/abcdef") }
            val event = Event.StateEvent(
                AvatarEventContent("mxc://localhost/123456"),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("set an empty avatar URL for normal rooms") {
            store.room.update(room) { Room(room, avatarUrl = "mxc://localhost/abcdef") }
            val event = Event.StateEvent(
                AvatarEventContent(""),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            store.room.get(room).value?.avatarUrl shouldBe null
        }

        should("set the avatar URL for direct rooms") {
            store.room.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                AvatarEventContent("mxc://localhost/123456"),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("set the avatar URL to a member of a direct room when the new avatar URL is empty") {
            store.room.update(room) { Room(room, isDirect = true, avatarUrl = "mxc://localhost/abcdef") }
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    DirectEventContent(mappings = mapOf(bob to setOf(room, RoomId("room2", "localhost"))))
                )
            )
            store.roomState.update(
                Event.StateEvent(
                    MemberEventContent(
                        avatarUrl = "mxc://localhost/123456",
                        membership = MemberEventContent.Membership.JOIN
                    ),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = bob.full
                )
            )
            val event = Event.StateEvent(
                AvatarEventContent(""),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            store.room.get(room).value?.avatarUrl shouldBe "mxc://localhost/123456"
        }
    }
})