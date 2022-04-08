package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownGlobalAccountDataEventContent(val raw: JsonObject, val eventType: String) :
    GlobalAccountDataEventContent