package net.folivo.trixnity.client.store

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.crypto.Key

data class StoredOlmSession(
    val sessionId: String,
    val senderKey: Key.Curve25519Key,
    val lastUsedAt: Instant,
    val createdAt: Instant = Clock.System.now(),
    val pickle: String
)