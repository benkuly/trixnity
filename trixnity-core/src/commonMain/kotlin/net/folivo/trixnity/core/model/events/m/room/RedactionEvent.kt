package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StandardUnsignedData

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-create">matrix spec</a>
 */
@Serializable
data class RedactionEvent(
    @SerialName("content") override val content: RedactionEventContent,
    @SerialName("redacts") val redacts: EventId,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("type") override val type: String = "m.room.redaction"
) : RoomEvent<RedactionEvent.RedactionEventContent> {
    @Serializable
    data class RedactionEventContent(
        @SerialName("reason")
        val reason: String?
    )
}