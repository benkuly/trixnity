package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
internal data class KnockRoomResponse(
    @SerialName("room_id") val roomId: RoomId
)