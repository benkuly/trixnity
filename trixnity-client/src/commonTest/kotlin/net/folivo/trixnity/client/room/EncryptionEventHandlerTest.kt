package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class EncryptionEventHandlerTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomEncryptionEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        cut = RoomEncryptionEventHandler(mockMatrixClientServerApiClient(json).first, roomStore)
    }

    afterTest {
        scope.cancel()
    }

    context(RoomEncryptionEventHandler::setEncryptionAlgorithm.name) {
        should("update set encryption algorithm") {
            roomStore.update(room) { simpleRoom.copy(membersLoaded = true) }
            cut.setEncryptionAlgorithm(
                Event.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            roomStore.get(room).first()?.encryptionAlgorithm shouldBe EncryptionAlgorithm.Megolm
            roomStore.get(room).first()?.membersLoaded shouldBe false
        }
    }
})