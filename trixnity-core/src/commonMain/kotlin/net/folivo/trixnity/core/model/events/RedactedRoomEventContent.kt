package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable

@Serializable
data class RedactedRoomEventContent(val eventType: String) : RoomEventContent