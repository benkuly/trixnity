package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun <T> Json.tryDeserializeOrElse(
    serializer: KSerializer<out T>,
    jsonObj: JsonObject,
    elseSerializer: (error: Exception) -> KSerializer<out T>
): T = try {
    decodeFromJsonElement(serializer, jsonObj)
} catch (error: Exception) {
    decodeFromJsonElement(elseSerializer(error), jsonObj)
}