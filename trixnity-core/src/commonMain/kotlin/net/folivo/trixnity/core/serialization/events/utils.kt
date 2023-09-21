package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun <T> Json.tryDeserializeOrElse(
    serializer: KSerializer<out T>,
    jsonElement: JsonElement,
    redactedSerializer: Lazy<KSerializer<out T>>? = null,
    elseSerializer: (error: Exception) -> KSerializer<out T>
): T = try {
    decodeFromJsonElement(serializer, jsonElement)
} catch (error: Exception) {
    if (redactedSerializer != null && jsonElement is JsonObject && jsonElement.isEmpty())
        decodeFromJsonElement(redactedSerializer.value, jsonElement)
    else decodeFromJsonElement(elseSerializer(error), jsonElement)
}