package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId

@Serializable
internal data class CreateRoomResponse(
    @SerialName("room_id") val roomId: RoomId
)