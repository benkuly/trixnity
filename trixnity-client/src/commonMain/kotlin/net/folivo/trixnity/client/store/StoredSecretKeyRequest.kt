package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import kotlin.time.Instant

@Serializable
data class StoredSecretKeyRequest(
    val content: SecretKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant
)