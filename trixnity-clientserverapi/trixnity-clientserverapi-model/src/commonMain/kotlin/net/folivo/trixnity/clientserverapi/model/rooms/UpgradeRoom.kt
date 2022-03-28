package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/upgrade")
@HttpMethod(POST)
data class UpgradeRoom(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<UpgradeRoom.Request, UpgradeRoom.Response> {
    @Serializable
    data class Request(
        @SerialName("new_version") val newVersion: String
    )

    @Serializable
    data class Response(
        @SerialName("replacement_room") val replacementRoom: RoomId
    )
}