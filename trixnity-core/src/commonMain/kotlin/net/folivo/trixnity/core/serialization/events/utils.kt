package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun <T> Json.tryDeserializeOrElse(
    serializer: KSerializer<out T>,
    jsonElement: JsonElement,
    elseSerializer: (error: Exception) -> KSerializer<out T>
): T = try {
    decodeFromJsonElement(serializer, jsonElement)
} catch (error: Exception) {
    decodeFromJsonElement(elseSerializer(error), jsonElement)
}

internal fun <T> Json.tryDeserializeOrElseNull(
    serializer: KSerializer<out T>,
    jsonElement: JsonElement,
    elseSerializer: (error: Exception) -> KSerializer<out T>
): T? = try {
    decodeFromJsonElement(serializer, jsonElement)
} catch (error: Exception) {
    try {
        decodeFromJsonElement(elseSerializer(error), jsonElement)
    } catch (deepError: Exception) {
        null
    }
}