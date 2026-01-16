package de.connect2x.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import kotlin.time.Instant

@Serializable
data class StoredOlmSession(
    val senderKey: Curve25519KeyValue,
    val sessionId: String,
    val lastUsedAt: Instant,
    val createdAt: Instant,
    val pickled: String,
    val initiatedByThisDevice: Boolean = false,
)