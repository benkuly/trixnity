package net.folivo.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.13/client-server-api/#post_matrixclientv3roomsroomidreport">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/report")
@HttpMethod(POST)
data class ReportRoom(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<ReportRoom.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String,
    )
}