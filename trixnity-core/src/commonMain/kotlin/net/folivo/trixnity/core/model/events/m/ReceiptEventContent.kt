package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import kotlin.jvm.JvmInline

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#receipts">matrix spec</a>
 */
@Serializable
@JvmInline
value class ReceiptEventContent(
    val events: Map<EventId, Map<ReceiptType, Map<UserId, Receipt>>>
) : EphemeralEventContent {
    @Serializable
    data class Receipt(@SerialName("ts") val timestamp: Long)
}