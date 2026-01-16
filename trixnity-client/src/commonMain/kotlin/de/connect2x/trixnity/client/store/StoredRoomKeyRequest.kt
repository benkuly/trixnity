package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.m.RoomKeyRequestEventContent
import kotlin.time.Instant

@Serializable
data class StoredRoomKeyRequest(
    val content: RoomKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant
)