package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

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
) : MatrixEndpoint<RedactEvent.Request, SendEventResponse> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
    )
}