package net.folivo.trixnity.core.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun canonicalJsonString(jsonElement: JsonElement): String {
    return Json.encodeToString(canonicalJson(jsonElement))
}

fun canonicalJson(jsonElement: JsonElement): JsonElement {
    return when (jsonElement) {
        is JsonObject -> JsonObject(
            jsonElement.mapValues { (_, value) -> canonicalJson(value) }
                .entries.sortedBy { it.key }.associateBy({ it.key }, { it.value })
        )
        is JsonArray -> JsonArray(jsonElement.map { entry -> canonicalJson(entry) })
        is JsonPrimitive -> jsonElement
    }
}