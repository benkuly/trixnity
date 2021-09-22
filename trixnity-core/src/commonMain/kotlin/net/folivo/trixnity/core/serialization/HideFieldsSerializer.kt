package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

open class HideFieldsSerializer<T : Any>(
    baseSerializer: KSerializer<T>,
    private vararg val hideFields: String,
) : JsonTransformingSerializer<T>(baseSerializer) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        return hideField(element)
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        return hideField(element)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun hideField(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element)
            hideFields.forEach { remove(it) }
        })
    }
}