package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownStateEventContent(val raw: JsonObject, val eventType: String) : StateEventContent