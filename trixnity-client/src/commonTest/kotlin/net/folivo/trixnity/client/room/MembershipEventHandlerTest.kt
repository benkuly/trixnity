package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class MembershipEventHandlerTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: MembershipEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        cut = MembershipEventHandler(
            UserInfo(alice, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            mockMatrixClientServerApiClient(json).first, roomStore
        )
    }

    afterTest {
        scope.cancel()
    }

    context(MembershipEventHandler::setOwnMembership.name) {
        should("set own membership of a room") {
            cut.setOwnMembership(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            roomStore.get(room).value?.membership shouldBe Membership.LEAVE
        }
    }
})