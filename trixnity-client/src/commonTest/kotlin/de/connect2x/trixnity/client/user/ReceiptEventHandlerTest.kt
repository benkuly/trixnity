package de.connect2x.trixnity.client.user

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.getInMemoryRoomUserStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap.Companion.roomMapOf
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.EphemeralEvent
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
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

    @Test
    fun deleteReadReceiptsOnNonJoin() = runTest {
        val roomId1 = RoomId("!room1")
        val roomId2 = RoomId("!room2")
        val roomId3 = RoomId("!room3")
        val roomId4 = RoomId("!room4")
        fun roomUserReceipt(roomId: RoomId) = roomUserReceipts(
            roomId, alice, receipts = mapOf(
                ReceiptType.Read to RoomUserReceipts.Receipt(
                    EventId("existingEvent"),
                    Receipt(0)
                )
            )
        )
        roomUserStore.updateReceipts(alice, roomId1) { roomUserReceipt(roomId1) }
        roomUserStore.updateReceipts(alice, roomId2) { roomUserReceipt(roomId2) }
        roomUserStore.updateReceipts(alice, roomId3) { roomUserReceipt(roomId3) }
        roomUserStore.updateReceipts(alice, roomId4) { roomUserReceipt(roomId4) }

        cut.deleteReadReceiptsOnNonJoin(
            SyncEvents(
                Sync.Response(
                    "",
                    Sync.Response.Rooms(
                        join = roomMapOf(roomId1 to Sync.Response.Rooms.JoinedRoom()),
                        knock = roomMapOf(roomId2 to Sync.Response.Rooms.KnockedRoom()),
                        invite = roomMapOf(roomId3 to Sync.Response.Rooms.InvitedRoom()),
                        leave = roomMapOf(roomId4 to Sync.Response.Rooms.LeftRoom())
                    )
                ), listOf()
            )
        )

        roomUserStore.getReceipts(alice, roomId1).first() shouldBe roomUserReceipt(roomId1)
        roomUserStore.getReceipts(alice, roomId2).first() shouldBe null
        roomUserStore.getReceipts(alice, roomId3).first() shouldBe null
        roomUserStore.getReceipts(alice, roomId4).first() shouldBe null

    }

}