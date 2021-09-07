package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UnknownEphemeralEventContent(val raw: JsonObject, val eventType: String) : EphemeralEventContent