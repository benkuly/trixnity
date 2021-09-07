package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-key-request">matrix spec</a>
 */
@Serializable
data class RoomKeyRequestEventContent(
    @SerialName("action")
    val action: Action,
    @SerialName("requesting_device_id")
    val requestingDeviceId: String,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("body")
    val body: RequestedKeyInfo? = null
) : ToDeviceEventContent {
    @Serializable
    enum class Action {
        @SerialName("request")
        REQUEST,

        @SerialName("request_cancellation")
        REQUEST_CANCELLATION
    }

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