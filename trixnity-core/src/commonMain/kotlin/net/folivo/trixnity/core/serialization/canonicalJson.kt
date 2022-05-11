package net.folivo.trixnity.core.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun canonicalJson(jsonObject: JsonObject): String {
    return Json.encodeToString(sortJsonObject(jsonObject))
}

inline fun <reified T : Any> canonicalJson(jsonObject: T): String {
    return canonicalJson(Json.encodeToJsonElement(jsonObject).jsonObject)
}

private fun sortJsonObject(jsonObject: JsonObject): JsonObject {
    return JsonObject(jsonObject.mapValues { (_, value) ->
        when (value) {
            is JsonObject -> sortJsonObject(value)
            is JsonArray -> JsonArray(value.map { entry -> if (entry is JsonObject) sortJsonObject(entry) else entry })
            else -> value
        }
    }.entries.sortedBy { it.key }.associateBy({ it.key }, { it.value }))
}