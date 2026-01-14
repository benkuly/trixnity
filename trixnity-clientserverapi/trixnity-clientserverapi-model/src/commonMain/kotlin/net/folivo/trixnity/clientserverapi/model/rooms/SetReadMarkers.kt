package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidread_markers">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/read_markers")
@HttpMethod(POST)
data class SetReadMarkers(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<SetReadMarkers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("m.fully_read") val fullyRead: EventId? = null,
        @SerialName("m.read") val read: EventId? = null,
        @SerialName("m.read.private") val privateRead: EventId? = null,
    )
}