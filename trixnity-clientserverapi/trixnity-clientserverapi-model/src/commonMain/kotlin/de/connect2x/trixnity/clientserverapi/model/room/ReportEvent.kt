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
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidreporteventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/report/{eventId}")
@HttpMethod(POST)
data class ReportEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<ReportEvent.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
        @SerialName("score") val score: Long?,
    )
}