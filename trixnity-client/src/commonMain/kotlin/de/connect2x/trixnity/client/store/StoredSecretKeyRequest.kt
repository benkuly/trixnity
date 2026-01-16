package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import kotlin.time.Instant

@Serializable
data class StoredSecretKeyRequest(
    val content: SecretKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant
)