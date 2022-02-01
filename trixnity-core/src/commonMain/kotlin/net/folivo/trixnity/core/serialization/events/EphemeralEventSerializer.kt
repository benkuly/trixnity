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
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.Event.EphemeralEvent
import net.folivo.trixnity.core.model.events.UnknownEphemeralEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class EphemeralEventSerializer(
    private val ephemeralEventContentSerializers: Set<EventContentSerializerMapping<out EphemeralEventContent>>,
) : KSerializer<EphemeralEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EphemeralEventSerializer")

    private val eventsContentLookupByType = ephemeralEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): EphemeralEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type]
            ?: UnknownEventContentSerializer(UnknownEphemeralEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(
                EphemeralEvent.serializer(contentSerializer), jsonObj
            )
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                EphemeralEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownEphemeralEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: EphemeralEvent<*>) {
        val content = value.content
        if (content is UnknownEphemeralEventContent) throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentSerializerMapping = ephemeralEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentSerializerMapping) { "event content type ${value.content::class} must be registered" }

        val addFields = mutableListOf("type" to contentSerializerMapping.type)
        val contentSerializer = contentSerializerMapping.serializer

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                EphemeralEvent.serializer(contentSerializer) as KSerializer<EphemeralEvent<*>>,
                *addFields.toTypedArray()
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}