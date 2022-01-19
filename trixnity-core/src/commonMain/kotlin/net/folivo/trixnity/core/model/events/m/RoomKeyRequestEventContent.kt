package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroom_key_request">matrix spec</a>
 */
@Serializable
data class RoomKeyRequestEventContent(
    @SerialName("action")
    val action: KeyRequestAction,
    @SerialName("requesting_device_id")
    val requestingDeviceId: String,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("body")
    val body: RequestedKeyInfo? = null
) : ToDeviceEventContent {

    @Serializable
    data class RequestedKeyInfo(
        @SerialName("room_id")
        val roomId: RoomId,
        @SerialName("sender_key")
        val senderKey: String,
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("algorithm")
        val algorithm: EncryptionAlgorithm
    )
}