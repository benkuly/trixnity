package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.MatrixId.*

@Serializable
class UnknownStateEvent(
    @SerialName("content") override val content: JsonObject,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: JsonObject? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "UNKNOWN"
) : StateEvent<JsonObject> {
}