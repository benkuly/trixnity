package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent
import kotlin.jvm.JvmInline

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#receipts">matrix spec</a>
 */
@Serializable
@JvmInline
value class ReceiptDataUnitContent(
    val receipts: Map<String, RoomReceipts>,
) : EphemeralDataUnitContent {
    @Serializable
    data class RoomReceipts(
        @SerialName("m.read")
        val read: UserReadReceipt
    ) {
        @Serializable
        data class UserReadReceipt(
            @SerialName("data")
            val data: ReadReceiptMetadata,
            @SerialName("event_ids")
            val eventIds: Set<EventId>
        ) {
            @Serializable
            data class ReadReceiptMetadata(
                @SerialName("ts")
                val ts: Long
            )
        }
    }
}