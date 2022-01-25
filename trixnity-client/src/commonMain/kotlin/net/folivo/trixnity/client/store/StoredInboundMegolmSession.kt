package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key

data class StoredInboundMegolmSession(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: RoomId,
    val firstKnownIndex: Long,
    val hasBeenBackedUp: Boolean,
    val isTrusted: Boolean,
    val senderSigningKey: Key.Ed25519Key,
    val forwardingCurve25519KeyChain: List<Key.Curve25519Key>,
    val pickled: String
)