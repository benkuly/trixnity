package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.serialization.canonicalJson

abstract class BaseEventSerializer<C : EventContent, E : Event<out C>>(
    name: String,
    private val mappings: EventContentToEventSerializerMappings<C, out E, *>,
    private val typeField: String = "type",
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("${name}Serializer")
    override fun deserialize(decoder: Decoder): E {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type =
            (jsonObj[typeField] as? JsonPrimitive)?.content ?: throw SerializationException("type must not be null")
        return decoder.json.decodeFromJsonElement(mappings[type], jsonObj)
    }

    override fun serialize(encoder: Encoder, value: E) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings[value.content].serializer as KSerializer<E>
        val jsonElement = encoder.json.encodeToJsonElement(serializer, value)
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}