package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroom_key">matrix spec</a>
 */
@Serializable
data class RoomKeyEventContent(
    @SerialName("room_id")
    val roomId: RoomId,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("session_key")
    val sessionKey: SessionKeyValue,
    @SerialName("algorithm")
    val algorithm: EncryptionAlgorithm
) : ToDeviceEventContent