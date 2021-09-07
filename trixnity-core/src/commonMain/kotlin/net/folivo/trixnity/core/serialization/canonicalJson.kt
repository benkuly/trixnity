package net.folivo.trixnity.core.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

fun canonicalJson(jsonObject: JsonObject): String {
    return Json.encodeToString(sortJsonObject(jsonObject))
}

private fun sortJsonObject(jsonObject: JsonObject): JsonObject {
    return JsonObject(jsonObject.mapValues { (_, value) ->
        when (value) {
            is JsonObject -> sortJsonObject(value)
            is JsonArray -> JsonArray(value.map { entry -> if (entry is JsonObject) sortJsonObject(entry) else entry })
            else -> value
        }
    }.toSortedMap())
}