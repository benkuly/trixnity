package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event.OlmEvent
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class OlmEventSerializer(
    private val eventContentSerializers: Set<EventContentSerializerMapping<out EventContent>>,
) : KSerializer<OlmEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OlmEventSerializer")

    override fun deserialize(decoder: Decoder): OlmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventContentSerializers.contentDeserializer(type)
        return try {
            decoder.json.decodeFromJsonElement(OlmEvent.serializer(contentSerializer), jsonObj)
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                OlmEvent.serializer(UnknownEventContentSerializer(type)), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: OlmEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = eventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                OlmEvent.serializer(serializer) as KSerializer<OlmEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}