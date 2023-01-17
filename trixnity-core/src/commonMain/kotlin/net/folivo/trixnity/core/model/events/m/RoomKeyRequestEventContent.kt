package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mroom_key_request">matrix spec</a>
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
        @SerialName("sender_key")
        @Deprecated(
            "This field provides no additional security or privacy benefit and must not be read from. " +
                    "It should still be included on outgoing messages (if the event for which keys are being requested for " +
                    "also has a sender_key), however must not be used to find the corresponding session. " +
                    "See m.megolm.v1.aes-sha2 for more information."
        )
        val senderKey: Key.Curve25519Key? = null
    )
}