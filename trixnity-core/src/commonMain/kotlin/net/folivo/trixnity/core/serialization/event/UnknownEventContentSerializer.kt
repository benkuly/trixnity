package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

class UnknownEventContentSerializer<T : Any>(
    serializer: KSerializer<T>,
    private val eventType: String
) : JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(mapOf("raw" to element, "eventType" to JsonPrimitive(eventType)))
    }
}