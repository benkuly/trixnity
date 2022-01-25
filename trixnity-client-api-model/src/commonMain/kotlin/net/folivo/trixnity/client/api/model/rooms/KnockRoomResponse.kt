package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class KnockRoomResponse(
    @SerialName("room_id") val roomId: RoomId
)