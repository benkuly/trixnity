package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}")
data class RedactEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val type: EventId,
    @SerialName("txnId") val tnxId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<RedactEvent.Request, SendEventResponse>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("reason") val reason: String?
    )
}