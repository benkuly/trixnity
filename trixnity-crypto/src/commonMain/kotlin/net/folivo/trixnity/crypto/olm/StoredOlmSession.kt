package net.folivo.trixnity.crypto.olm

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.Key

@Serializable
data class StoredOlmSession(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val lastUsedAt: Instant,
    val createdAt: Instant,
    val pickled: String,
    val initiatedByThisDevice: Boolean = false,
)