package net.folivo.trixnity.client.store

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

@Serializable
data class RoomUser(
    val roomId: RoomId,
    val userId: UserId,
    val name: String,
    val event: @Contextual StateBaseEvent<MemberEventContent>,
    val receipts: Map<ReceiptType, Receipt> = mapOf(),
) {
    @Serializable
    data class Receipt(
        val eventId: EventId,
        val receipt: ReceiptEventContent.Receipt
    )
}