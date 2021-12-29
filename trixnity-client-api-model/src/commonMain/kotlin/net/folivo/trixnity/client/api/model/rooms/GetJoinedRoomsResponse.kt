package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class GetJoinedRoomsResponse(
    @SerialName("joined_rooms") val joinedRooms: Set<RoomId>
)