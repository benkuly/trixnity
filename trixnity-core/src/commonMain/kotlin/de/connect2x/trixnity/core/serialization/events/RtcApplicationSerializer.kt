package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.UnknownRtcApplication
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@MSC4143
class RtcApplicationSerializer(
    private val mappings: RtcApplicationSerializerMappings,
) : KSerializer<RtcApplication> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("de.connect2x.trixnity.core.model.events.m.rtc.RtcApplication")

    override fun serialize(encoder: Encoder, value: RtcApplication) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("RtcApplicationSerializer only supports JSON encoding")

        @Suppress("UNCHECKED_CAST")
        val mapping = mappings.find { it.kClass.isInstance(value) }
                as RtcApplicationSerializerMapping<RtcApplication>?
        if (mapping != null) {
            val element = jsonEncoder.json.encodeToJsonElement(mapping.serializer, value).jsonObject
            jsonEncoder.encodeJsonElement(JsonObject(buildMap {
                put("type", JsonPrimitive(mapping.type))
                putAll(element)
            }))
        } else {
            check(value is UnknownRtcApplication) {
                "No RtcApplicationSerializerMapping found for ${value::class} and it is not UnknownRtcApplication"
            }
            jsonEncoder.encodeJsonElement(value.raw)
        }
    }

    override fun deserialize(decoder: Decoder): RtcApplication {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("RtcApplicationSerializer only supports JSON decoding")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val type = (element["type"] as? JsonPrimitive)?.content
        val mapping = if (type != null) mappings.find { it.type == type } else null
        return if (mapping != null) {
            @Suppress("UNCHECKED_CAST")
            jsonDecoder.json.decodeFromJsonElement(
                mapping.serializer as KSerializer<RtcApplication>,
                element
            )
        } else {
            UnknownRtcApplication(element)
        }
    }
}
