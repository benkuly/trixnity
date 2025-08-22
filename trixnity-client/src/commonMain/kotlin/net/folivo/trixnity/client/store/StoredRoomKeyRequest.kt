package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import kotlin.time.Instant

@Serializable
data class StoredRoomKeyRequest(
    val content: RoomKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant
)