package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

class UnknownEventContentSerializer<T : Any>(
    serializer: KSerializer<T>,
    private val eventType: String
) : JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(mapOf("raw" to element, "eventType" to JsonPrimitive(eventType)))
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val raw = element.jsonObject["raw"]
        requireNotNull(raw)
        return raw
    }
}