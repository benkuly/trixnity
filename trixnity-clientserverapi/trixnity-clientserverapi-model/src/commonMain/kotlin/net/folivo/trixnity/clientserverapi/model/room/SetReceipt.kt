package net.folivo.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.ReceiptType

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