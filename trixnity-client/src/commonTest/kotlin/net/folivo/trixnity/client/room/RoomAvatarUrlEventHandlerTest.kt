package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomAvatarUrlEventHandlerTest : ShouldSpec({
    timeout = 5_000
    val room = RoomId("room", "localhost")
    val bob = UserId("bob", "localhost")
    val alice = UserId("alice", "localhost")
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomAvatarUrlEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        cut = RoomAvatarUrlEventHandler(
            UserInfo(alice, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            mockMatrixClientServerApiClient(json).first,
            roomStore, roomStateStore, globalAccountDataStore,
            RepositoryTransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
        scope.cancel()
    }

    context(RoomAvatarUrlEventHandler::setAvatarUrlForMemberUpdates.name) {
        should("update the room's avatar URL when the room is a direct room") {
            roomStore.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                MemberEventContent(
                    avatarUrl = "mxc://localhost/123456",
                    membership = Membership.JOIN,
                ),
                EventId("1"),
                alice,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("do nothing when the room is not a direct room") {
            roomStore.update(room) { Room(room, isDirect = false) }
            val event = Event.StateEvent(
                MemberEventContent(
                    avatarUrl = "mxc://localhost/123456",
                    membership = Membership.JOIN,
                ),
                EventId("1"),
                alice,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            roomStore.get(room).first()?.avatarUrl shouldBe null
        }

        should("use the membership event of other user and not own (which is the invitation we might have sent)") {
            roomStore.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                // invitation
                MemberEventContent(
                    avatarUrl = "mxc://localhost/abcdef",
                    membership = Membership.JOIN,
                ),
                EventId("1"),
                alice,
                room,
                0L,
                stateKey = alice.full,
            )

            cut.setAvatarUrlForMemberUpdates(event)

            roomStore.get(room).first()?.avatarUrl shouldBe null
        }
    }

    context(RoomAvatarUrlEventHandler::setAvatarUrlForAvatarEvents.name) {
        should("set the avatar URL for normal rooms") {
            roomStore.update(room) { Room(room, avatarUrl = "mxc://localhost/abcdef") }
            val event = Event.StateEvent(
                AvatarEventContent("mxc://localhost/123456"),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("set an empty avatar URL for normal rooms") {
            roomStore.update(room) { Room(room, avatarUrl = "mxc://localhost/abcdef") }
            val event = Event.StateEvent(
                AvatarEventContent(""),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            roomStore.get(room).first()?.avatarUrl shouldBe null
        }

        should("set the avatar URL for direct rooms") {
            roomStore.update(room) { Room(room, isDirect = true) }
            val event = Event.StateEvent(
                AvatarEventContent("mxc://localhost/123456"),
                EventId("1"),
                bob,
                room,
                0L,
                stateKey = bob.full,
            )

            cut.setAvatarUrlForAvatarEvents(event)

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }

        should("set the avatar URL to a member of a direct room when the new avatar URL is empty") {
            roomStore.update(room) { Room(room, isDirect = true, avatarUrl = "mxc://localhost/abcdef") }
            globalAccountDataStore.save(
                Event.GlobalAccountDataEvent(
                    DirectEventContent(mappings = mapOf(bob to setOf(room, RoomId("room2", "localhost"))))
                )
            )
            roomStateStore.save(
                Event.StateEvent(
                    MemberEventContent(
                        avatarUrl = "mxc://localhost/123456",
                        membership = Membership.JOIN
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

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }
    }
})