package net.folivo.trixnity.appservice.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId

@Serializable
internal data class SetRoomAliasRequest(
    @SerialName("room_id") val roomId: RoomId,
)