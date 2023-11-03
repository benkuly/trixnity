package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType

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