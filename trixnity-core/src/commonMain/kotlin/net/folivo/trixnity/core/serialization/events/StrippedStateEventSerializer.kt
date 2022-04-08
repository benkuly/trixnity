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
import net.folivo.trixnity.core.model.events.Event.StrippedStateEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class StrippedStateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
) : KSerializer<StrippedStateEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StrippedStateEventSerializer")

    override fun deserialize(decoder: Decoder): StrippedStateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val isRedacted = jsonObj["unsigned"]?.jsonObject?.get("redacted_because") != null
        val contentSerializer = stateEventContentSerializers.contentDeserializer(type, isRedacted)
        return try {
            decoder.json.decodeFromJsonElement(
                StrippedStateEvent.serializer(contentSerializer),
                jsonObj
            )
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                StrippedStateEvent.serializer(UnknownStateEventContentSerializer(type)), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: StrippedStateEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = stateEventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                StrippedStateEvent.serializer(serializer) as KSerializer<StrippedStateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}