package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject

data class UnknownEphemeralDataUnitContent(val raw: JsonObject, val eventType: String) : EphemeralDataUnitContent