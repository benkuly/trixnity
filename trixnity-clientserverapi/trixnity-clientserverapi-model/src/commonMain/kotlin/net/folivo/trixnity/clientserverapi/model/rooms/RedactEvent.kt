package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3roomsroomidredacteventidtxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}")
@HttpMethod(PUT)
data class RedactEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("txnId") val txnId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<RedactEvent.Request, SendEventResponse> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
    )
}