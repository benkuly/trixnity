package de.connect2x.trixnity.core.serialization.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationSlot
import de.connect2x.trixnity.core.serialization.events.RtcApplicationSerializerMapping
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
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
class RtcApplicationSlotSerializer(private val mappings: Set<RtcApplicationSerializerMapping<*, *>>) :
    KSerializer<RtcApplicationSlot> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationSlot")

    override fun serialize(encoder: Encoder, value: RtcApplicationSlot) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val mapping =
            mappings.find { it.applicationClass.isInstance(value) }
                as RtcApplicationSerializerMapping<RtcApplicationSlot, RtcApplicationMember>?
        if (mapping != null) {
            val element = encoder.json.encodeToJsonElement(mapping.applicationSerializer, value).jsonObject
            encoder.encodeJsonElement(
                JsonObject(
                    buildMap {
                        put("type", JsonPrimitive(mapping.type))
                        putAll(element)
                    }
                )
            )
        } else {
            check(value is RtcApplicationSlot.Unknown) {
                "No RtcApplicationSlotSerializerMapping found for ${value::class} and it is not RtcApplicationSlot.Unknown"
            }
            encoder.encodeJsonElement(value.raw)
        }
    }

    override fun deserialize(decoder: Decoder): RtcApplicationSlot {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val type = (element["type"] as? JsonPrimitive)?.content ?: throw SerializationException("type missing")
        val mapping = mappings.find { it.type == type }
        return if (mapping != null) {
            @Suppress("UNCHECKED_CAST")
            decoder.json.decodeFromJsonElement(
                mapping.applicationSerializer as KSerializer<RtcApplicationSlot>,
                element,
            )
        } else {
            RtcApplicationSlot.Unknown(type, element)
        }
    }
}
