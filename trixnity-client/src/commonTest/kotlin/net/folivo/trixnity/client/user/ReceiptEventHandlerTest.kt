package net.folivo.trixnity.client.user

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryRoomUserStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class ReceiptEventHandlerTest : TrixnityBaseTest() {

    private val room = RoomId("!room:localhost")
    private val alice = UserId("alice", "localhost")
    private val bob = UserId("bob", "localhost")

    private val json = createMatrixEventJson()

    private val roomUserStore = getInMemoryRoomUserStore()
    private val cut = ReceiptEventHandler(
        mockMatrixClientServerApiClient(json = json),
        roomUserStore,
        TransactionManagerMock(),
    )


    private fun roomUserReceipts(
        roomId: RoomId,
        userId: UserId,
        receipts: Map<ReceiptType, RoomUserReceipts.Receipt> = mapOf()
    ) = RoomUserReceipts(
        roomId,
        userId,
        receipts = receipts,
    )

    @Test
    fun `setReadReceipts » do nothing when the user in the receipt could not be found`() = runTest {
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

    @Test
    fun `setReadReceipts » do store receipts from unknown users`() = runTest {
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

    @Test
    fun `setReadReceipts » set the last read message for a user with no existing receipts in this room`() =
        runTest {
            val eventId1 = EventId("eventId1")
            val eventId2 = EventId("eventId2")
            val aliceRoomUser = roomUserReceipts(room, alice)
            val bobRoomUser = roomUserReceipts(room, alice)
            roomUserStore.apply {
                updateReceipts(alice, room) { aliceRoomUser }
                updateReceipts(bob, room) { bobRoomUser }
            }
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

    @Test
    fun `setReadReceipts » replace the last read message of a user when a new last message is received`() =
        runTest {
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