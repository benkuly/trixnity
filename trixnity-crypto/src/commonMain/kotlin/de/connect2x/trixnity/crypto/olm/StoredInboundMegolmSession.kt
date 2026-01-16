package de.connect2x.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue

@Serializable
data class StoredInboundMegolmSession(
    val senderKey: Curve25519KeyValue,
    val senderSigningKey: Ed25519KeyValue,
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
    val forwardingCurve25519KeyChain: List<Curve25519KeyValue>,
    val pickled: String
)