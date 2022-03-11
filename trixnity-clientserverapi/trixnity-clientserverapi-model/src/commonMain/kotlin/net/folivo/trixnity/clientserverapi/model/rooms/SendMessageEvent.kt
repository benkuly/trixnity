package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/send/{type}/{txnId}")
data class SendMessageEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("txnId") val tnxId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<MessageEventContent, SendEventResponse>() {
    @Transient
    override val method = Put
}