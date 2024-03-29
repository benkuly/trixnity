package net.folivo.trixnity.crypto.olm

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.Key

@Serializable
data class StoredOlmSession(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val lastUsedAt: Instant,
    val createdAt: Instant = Clock.System.now(),
    val pickled: String
)