package net.folivo.trixnity.client.user

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryRoomUserStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class ReceiptEventHandlerTest : ShouldSpec({
    timeout = 5_000
    val room = RoomId("room", "localhost")
    val alice = UserId("alice", "localhost")
    val bob = UserId("bob", "localhost")
    lateinit var roomUserStore: RoomUserStore
    lateinit var scope: CoroutineScope

    lateinit var cut: ReceiptEventHandler

    val json = createMatrixEventJson()
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomUserStore = getInMemoryRoomUserStore(scope)
        cut = ReceiptEventHandler(
            mockMatrixClientServerApiClient(json).first,
            roomUserStore,
            TransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    fun roomUserReceipts(
        roomId: RoomId,
        userId: UserId,
        receipts: Map<ReceiptType, RoomUserReceipts.Receipt> = mapOf()
    ): RoomUserReceipts {
        return RoomUserReceipts(
            roomId,
            userId,
            receipts = receipts,
        )
    }

    context(ReceiptEventHandler::setReadReceipts.name) {
        should("do nothing when the user in the receipt could not be found") {
            val existingRoomUser = roomUserReceipts(room, alice)
            roomUserStore.updateReceipts(alice, room) { existingRoomUser }
            val event = EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        EventId("eventId") to mapOf(
                            ReceiptType.Read to mapOf(
                                UserId("unknownUser", "localhost") to Receipt(0L)
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(listOf(event))

            roomUserStore.getReceipts(alice, room).first() shouldBeSameInstanceAs existingRoomUser
        }

        should("do store receipts from unknown users") {
            val existingRoomUser = roomUserReceipts(room, alice)
            val unknownUserId = UserId("unknownUser", "localhost")
            roomUserStore.updateReceipts(alice, room) { existingRoomUser }
            val event = EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        EventId("eventId") to mapOf(
                            ReceiptType.Unknown("awesome") to mapOf(
                                unknownUserId to Receipt(0L)
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(listOf(event))

            roomUserStore.getReceipts(unknownUserId, room).first()?.receipts shouldBe
                    mapOf(ReceiptType.Unknown("awesome") to RoomUserReceipts.Receipt(EventId("eventId"), Receipt(0L)))
        }

        should("set the last read message for a user with no existing receipts in this room") {
            val eventId1 = EventId("eventId1")
            val eventId2 = EventId("eventId2")
            val aliceRoomUser = roomUserReceipts(room, alice)
            val bobRoomUser = roomUserReceipts(room, alice)
            roomUserStore.updateReceipts(alice, room) { aliceRoomUser }
            roomUserStore.updateReceipts(bob, room) { bobRoomUser }
            val event = EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        eventId1 to mapOf(
                            ReceiptType.FullyRead to mapOf(
                                alice to Receipt(0L)
                            ),
                            ReceiptType.Read to mapOf(
                                alice to Receipt(1L),
                                bob to Receipt(24L)
                            )
                        ),
                        eventId2 to mapOf(
                            ReceiptType.Read to mapOf(
                                alice to Receipt(2L)
                            ),
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(listOf(event))

            roomUserStore.getReceipts(alice, room).first()?.receipts?.get(ReceiptType.Read) shouldBe
                    RoomUserReceipts.Receipt(eventId2, Receipt(2L))
            roomUserStore.getReceipts(bob, room).first()?.receipts?.get(ReceiptType.Read) shouldBe
                    RoomUserReceipts.Receipt(eventId1, Receipt(24L))
        }

        should("replace the last read message of a user when a new last message is received") {
            val existingEventId = EventId("existingEvent")
            val existingRoomUser = roomUserReceipts(
                room, alice, receipts = mapOf(
                    ReceiptType.Read to RoomUserReceipts.Receipt(
                        existingEventId,
                        Receipt(0)
                    )
                )
            )
            roomUserStore.updateReceipts(alice, room) { existingRoomUser }
            val eventId = EventId("eventId")
            val event = EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        eventId to mapOf(
                            ReceiptType.Read to mapOf(
                                alice to Receipt(3)
                            )
                        )
                    )
                ),
                roomId = room,
            )
            cut.setReadReceipts(listOf(event))
            roomUserStore.getReceipts(alice, room).first()?.receipts?.get(ReceiptType.Read) shouldBe
                    RoomUserReceipts.Receipt(eventId, Receipt(3L))
        }
    }
})