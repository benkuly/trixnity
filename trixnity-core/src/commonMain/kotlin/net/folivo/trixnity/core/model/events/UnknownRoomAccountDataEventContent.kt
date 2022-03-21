package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownRoomAccountDataEventContent(val raw: JsonObject, val eventType: String) : RoomAccountDataEventContent