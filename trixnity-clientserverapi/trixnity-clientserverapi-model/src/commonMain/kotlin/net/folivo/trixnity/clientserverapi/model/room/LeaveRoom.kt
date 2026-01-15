package net.folivo.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidleave">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/leave")
@HttpMethod(POST)
data class LeaveRoom(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<LeaveRoom.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
    )
}