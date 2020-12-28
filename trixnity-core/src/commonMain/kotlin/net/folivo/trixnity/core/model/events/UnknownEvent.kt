package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class UnknownEvent(
    @SerialName("type") override val type: String = "UNKNOWN",
    @SerialName("content") override val content: JsonObject
) : Event<JsonObject> {
}