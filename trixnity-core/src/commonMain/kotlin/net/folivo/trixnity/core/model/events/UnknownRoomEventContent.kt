package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownRoomEventContent(val raw: JsonObject, val eventType: String) : RoomEventContent