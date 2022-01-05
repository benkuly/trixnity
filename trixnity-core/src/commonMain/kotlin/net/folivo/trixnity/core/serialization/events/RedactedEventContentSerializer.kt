package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

class RedactedEventContentSerializer<T : Any>(
    serializer: KSerializer<T>,
    private val eventType: String
) : JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(mapOf("eventType" to JsonPrimitive(eventType)))
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        return JsonObject(mapOf())
    }
}