package net.folivo.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key

@Serializable
data class StoredInboundMegolmSession(
    val senderKey: Key.Curve25519Key,
    val senderSigningKey: Key.Ed25519Key,
    val sessionId: String,
    val roomId: RoomId,
    val firstKnownIndex: Long,
    val hasBeenBackedUp: Boolean,
    /**
     * This means, that we can trust the communication channel from which we received the session from.
     * For example the key backup cannot be trusted due to async encryption.
     * This does NOT mean, that we trust this megolm session. It needs to be checked whether we trust the sender key.
     */
    val isTrusted: Boolean,
    val forwardingCurve25519KeyChain: List<Key.Curve25519Key>,
    val pickled: String
)