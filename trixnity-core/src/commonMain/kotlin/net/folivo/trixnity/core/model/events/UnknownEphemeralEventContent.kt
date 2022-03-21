package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownEphemeralEventContent(val raw: JsonObject, val eventType: String) : EphemeralEventContent