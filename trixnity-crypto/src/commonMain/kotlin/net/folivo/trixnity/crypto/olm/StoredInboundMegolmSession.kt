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
    val isTrusted: Boolean,
    val forwardingCurve25519KeyChain: List<Key.Curve25519Key>,
    val pickled: String
)