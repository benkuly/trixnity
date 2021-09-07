package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-key">matrix spec</a>
 */
@Serializable
data class RoomKeyEventContent(
    @SerialName("room_id")
    val roomId: RoomId,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("session_key")
    val sessionKey: String,
    @SerialName("algorithm")
    val algorithm: EncryptionAlgorithm
) : ToDeviceEventContent