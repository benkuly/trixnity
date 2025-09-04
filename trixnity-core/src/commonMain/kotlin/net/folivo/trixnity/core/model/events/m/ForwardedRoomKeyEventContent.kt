package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.ExportedSessionKeyValue
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mforwarded_room_key">matrix spec</a>
 */
@Serializable
data class ForwardedRoomKeyEventContent(
    @SerialName("room_id")
    val roomId: RoomId,
    @SerialName("sender_key")
    val senderKey: Curve25519KeyValue,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("session_key")
    val sessionKey: ExportedSessionKeyValue,
    @SerialName("sender_claimed_ed25519_key")
    val senderClaimedKey: Ed25519KeyValue,
    @SerialName("forwarding_curve25519_key_chain")
    val forwardingKeyChain: List<Curve25519KeyValue>,
    @SerialName("algorithm")
    val algorithm: EncryptionAlgorithm
) : ToDeviceEventContent