package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mroom_key">matrix spec</a>
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