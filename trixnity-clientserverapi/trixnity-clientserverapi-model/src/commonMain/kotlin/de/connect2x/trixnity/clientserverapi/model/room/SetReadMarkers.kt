package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

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