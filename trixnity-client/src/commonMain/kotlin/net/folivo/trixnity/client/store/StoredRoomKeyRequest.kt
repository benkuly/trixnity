package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent

@Serializable
data class StoredRoomKeyRequest(
    val content: RoomKeyRequestEventContent,
    val receiverDeviceIds: Set<String>,
    val createdAt: Instant
)