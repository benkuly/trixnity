package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

// needed for ContextualStateEventContentSerializer to work
internal class PutTypeIntoPrevContentSerializer<T : Any>(baseSerializer: KSerializer<T>) :
    JsonTransformingSerializer<T>(baseSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val event = element.jsonObject
        val type = checkNotNull(event["type"])
        val unsigned = event["unsigned"] as? JsonObject
        val prevContent = unsigned?.get("prev_content") as? JsonObject
        return if (prevContent != null) {
            JsonObject(buildMap {
                putAll(event)
                put("unsigned", JsonObject(buildMap {
                    putAll(unsigned)
                    put("prev_content", JsonObject(buildMap {
                        putAll(prevContent)
                        put("type", type)
                    }))
                }))
            })
        } else element
    }
}