package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

open class HideDiscriminatorSerializer<T : Any>(
    baseSerializer: KSerializer<T>,
    private val discriminatorField: String,
    private val discriminatorValue: String
) : JsonTransformingSerializer<T>(baseSerializer) {
    @ExperimentalStdlibApi
    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element)
            remove(discriminatorField)
        })
    }

    @ExperimentalStdlibApi
    override fun transformSerialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element)
            put(discriminatorField, JsonPrimitive(discriminatorValue))
        })
    }
}