package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent

@Serializable
data class StoredSecretKeyRequest(
    @Serializable
    val content: SecretKeyRequestEventContent,
    @Serializable
    val receiverDeviceIds: Set<String>,
    @Serializable
    val creationTimestamp: Long
)