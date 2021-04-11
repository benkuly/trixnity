package net.folivo.trixnity.appservice.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId

@Serializable
data class GetRoomAliasResponse(
    @SerialName("room_id") val roomId: RoomId,
    @SerialName("servers") val servers: List<String>
)