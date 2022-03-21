package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownEventContent(val raw: JsonObject, val eventType: String) : EventContent