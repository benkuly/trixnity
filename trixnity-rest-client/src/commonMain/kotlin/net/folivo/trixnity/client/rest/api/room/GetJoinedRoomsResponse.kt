package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId

@Serializable
internal data class GetJoinedRoomsResponse(
    @SerialName("joined_rooms") val joinedRooms: Set<RoomId>
)