package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownToDeviceEventContent(val raw: JsonObject, val eventType: String) : ToDeviceEventContent