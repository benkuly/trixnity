package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt.ReadReceipt
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.serialization.createMatrixJson

class RoomServiceReadReceiptsTest : ShouldSpec({
    timeout = 5_000
    val room = RoomId("room", "localhost")
    val alice = UserId("alice", "localhost")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var scope: CoroutineScope
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomService

    val json = createMatrixJson()
    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = RoomService(
            alice,
            store,
            mockMatrixClientServerApiClient(json).first,
            OlmEventServiceMock(),
            KeyBackupServiceMock(),
            UserServiceMock(),
            MediaServiceMock(),
            currentSyncState
        )
    }

    afterTest {
        scope.cancel()
        storeScope.cancel()
    }

    context(RoomService::setReadReceipts.name) {

        should("do nothing when the user in the receipt could not be found") {
            val existingRoomUser = roomUser(room, alice)
            store.roomUser.update(alice, room) { existingRoomUser }
            val event = Event.EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        EventId("eventId") to setOf(
                            ReadReceipt(
                                read = mapOf(
                                    UserId("unknownUser", "localhost") to ReadReceipt.ReadEvent(0L)
                                )
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(event)

            store.roomUser.get(alice, room) shouldBeSameInstanceAs existingRoomUser
        }

        should("do nothing on unknown receipt events") {
            val existingRoomUser = roomUser(room, alice)
            store.roomUser.update(alice, room) { existingRoomUser }
            val event = Event.EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        EventId("eventId") to setOf(
                            Receipt.Unknown(
                                type = "awesome",
                                raw = JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(event)

            store.roomUser.get(alice, room) shouldBeSameInstanceAs existingRoomUser
        }

        should("set the last read message for a user with no existing receipts in this room") {
            val eventId = EventId("eventId")
            val existingRoomUser = roomUser(room, alice)
            store.roomUser.update(alice, room) { existingRoomUser }
            val event = Event.EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        eventId to setOf(
                            ReadReceipt(
                                read = mapOf(
                                    alice to ReadReceipt.ReadEvent(0L)
                                )
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(event)

            store.roomUser.get(alice, room)?.lastReadMessage shouldBe eventId
        }

        should("replace the last read message of a user when a new last message is received") {
            val existingEventId = EventId("existingEvent")
            val existingRoomUser = roomUser(room, alice, lastReadMessage = existingEventId)
            store.roomUser.update(alice, room) { existingRoomUser }
            val eventId = EventId("eventId")
            val event = Event.EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        eventId to setOf(
                            ReadReceipt(
                                read = mapOf(
                                    alice to ReadReceipt.ReadEvent(0L)
                                )
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(event)
            store.roomUser.get(alice, room)?.lastReadMessage shouldBe eventId
        }

        should("set the last read message even when unknown receipt types are encountered") {
            val eventId = EventId("eventId")
            val existingRoomUser = roomUser(room, alice)
            store.roomUser.update(alice, room) { existingRoomUser }
            val event = Event.EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        eventId to setOf(
                            Receipt.Unknown(
                                type = "awesome",
                                raw = JsonObject(mapOf("dino" to JsonPrimitive("unicorn")))
                            ),
                            ReadReceipt(
                                read = mapOf(
                                    alice to ReadReceipt.ReadEvent(0L)
                                )
                            ),
                            Receipt.Unknown(
                                type = "awesome",
                                raw = JsonObject(mapOf("unicorn" to JsonPrimitive("dino")))
                            ),
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(event)

            store.roomUser.get(alice, room)?.lastReadMessage shouldBe eventId
        }
    }
})

fun roomUser(roomId: RoomId, userId: UserId, lastReadMessage: EventId? = null): RoomUser {
    return RoomUser(
        roomId,
        userId,
        userId.full,
        event = Event.StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("event"),
            UserId("user", "server"),
            roomId,
            0L,
            stateKey = ""
        ),
        lastReadMessage = lastReadMessage,
    )
}
