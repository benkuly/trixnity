package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.EmptyEventContent
import net.folivo.trixnity.core.model.events.Event.OlmEvent
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class OlmEventSerializer(
    private val eventContentSerializers: Set<EventContentSerializerMapping<out EventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<OlmEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OlmEventSerializer")

    private val eventsContentLookupByType = eventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): OlmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type]
            ?: UnknownEventContentSerializer(EmptyEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(OlmEvent.serializer(contentSerializer), jsonObj)
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                OlmEvent.serializer(
                    UnknownEventContentSerializer(
                        EmptyEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: OlmEvent<*>) {
        val content = value.content
        if (content is EmptyEventContent) throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentSerializerMapping = eventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentSerializerMapping) { "event content type ${value.content::class} must be registered" }

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            AddFieldsSerializer(
                OlmEvent.serializer(contentSerializerMapping.serializer) as KSerializer<OlmEvent<*>>,
                "type" to contentSerializerMapping.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}