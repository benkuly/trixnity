package net.folivo.trixnity.crypto.olm

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

@Serializable
data class StoredOlmSession(
    val senderKey: Curve25519KeyValue,
    val sessionId: String,
    val lastUsedAt: Instant,
    val createdAt: Instant,
    val pickled: String,
    val initiatedByThisDevice: Boolean = false,
)