package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class GetRoomAliasResponse(
    @SerialName("room_id") val roomId: RoomId,
    @SerialName("servers") val servers: List<String>
)