package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/receipt/{receiptType}/{eventId}")
@HttpMethod(POST)
data class SetReceipt(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("receiptType") val receiptType: ReceiptType,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Unit> {
    @Serializable
    enum class ReceiptType {
        @SerialName("m.read")
        READ
    }
}