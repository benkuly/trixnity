package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.ReceiptType

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidreceiptreceipttypeeventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/receipt/{receiptType}/{eventId}")
@HttpMethod(POST)
data class SetReceipt(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("receiptType") val receiptType: ReceiptType,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<SetReceipt.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("thread_id") val threadId: EventId? = null,
    )
}