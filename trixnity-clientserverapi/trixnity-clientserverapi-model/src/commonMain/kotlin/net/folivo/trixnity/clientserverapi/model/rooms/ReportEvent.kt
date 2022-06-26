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
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#post_matrixclientv3roomsroomidreporteventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/report/{eventId}")
@HttpMethod(POST)
data class ReportEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<ReportEvent.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String?,
        @SerialName("score") val score: Long?,
    )
}