package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UnknownToDeviceEventContent(val raw: JsonObject, val eventType: String) : ToDeviceEventContent