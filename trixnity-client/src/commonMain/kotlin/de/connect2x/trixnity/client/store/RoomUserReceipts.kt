package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType

@Serializable
data class RoomUserReceipts(
    val roomId: RoomId,
    val userId: UserId,
    val receipts: Map<ReceiptType, Receipt> = mapOf(),
) {
    @Serializable
    data class Receipt(
        val eventId: EventId,
        val receipt: ReceiptEventContent.Receipt
    )
}