package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-forwarded-room-key">matrix spec</a>
 */
@Serializable
data class ForwardedRoomKeyEventContent(
    @SerialName("room_id")
    val roomId: RoomId,
    @SerialName("sender_key")
    val senderKey: Curve25519Key,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("session_key")
    val sessionKey: String,
    @SerialName("sender_claimed_ed25519_key")
    val senderClaimedKey: Ed25519Key,
    @SerialName("forwarding_curve25519_key_chain")
    val forwardingKeyChain: List<Curve25519Key>,
    @SerialName("algorithm")
    val algorithm: EncryptionAlgorithm
) : ToDeviceEventContent