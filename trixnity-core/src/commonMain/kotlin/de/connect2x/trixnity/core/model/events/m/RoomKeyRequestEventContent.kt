package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroom_key_request">matrix spec</a>
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
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("algorithm")
        val algorithm: EncryptionAlgorithm,
    )
}