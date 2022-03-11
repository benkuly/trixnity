package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

@Serializable
@Resource("/_matrix/client/v3/sendToDevice/{type}/{txnId}")
data class SendToDevice<C : ToDeviceEventContent>(
    @SerialName("type") val type: String,
    @SerialName("txnId") val tnxId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SendToDevice.Request<C>, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request<C : ToDeviceEventContent>(
        @SerialName("messages") val messages: Map<UserId, Map<String, C>>
    )
}