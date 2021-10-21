package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UnknownGlobalAccountDataEventContent(val raw: JsonObject, val eventType: String) :
    GlobalAccountDataEventContent