package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

open class AddFieldsSerializer<T : Any>(
    baseSerializer: KSerializer<T>,
    private vararg val fields: Pair<String, String>
) : JsonTransformingSerializer<T>(baseSerializer) {
    @ExperimentalStdlibApi
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return addField(element)
    }

    @ExperimentalStdlibApi
    override fun transformSerialize(element: JsonElement): JsonElement {
        return addField(element)
    }

    @ExperimentalStdlibApi
    private fun addField(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element)
            fields.forEach { put(it.first, JsonPrimitive(it.second)) }
        })
    }
}