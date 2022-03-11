package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/receipt/{receiptType}/{eventId}")
data class SetReceipt(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("receiptType") val receiptType: ReceiptType,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    enum class ReceiptType {
        @SerialName("m.read")
        READ
    }
}