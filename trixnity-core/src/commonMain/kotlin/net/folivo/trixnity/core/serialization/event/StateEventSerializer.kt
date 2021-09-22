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
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class StateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<StateEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StateEventSerializer")

    private val eventsContentLookupByType = stateEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): StateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val isRedacted = jsonObj["unsigned"]?.jsonObject?.get("redacted_because") != null
        val contentSerializer =
            if (!isRedacted)
                eventsContentLookupByType[type]
                    ?: UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)
            else RedactedEventContentSerializer(RedactedStateEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(
                StateEvent.serializer(contentSerializer), jsonObj
            )
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                StateEvent.serializer(UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: StateEvent<*>) {
        val content = value.content
        if (content is UnknownStateEventContent || value.content is RedactedStateEventContent)
            throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentDescriptor = stateEventContentSerializers.find { it.kClass.isInstance(content) }
        requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            AddFieldsSerializer(
                StateEvent.serializer(contentDescriptor.serializer) as KSerializer<StateEvent<*>>,
                "type" to contentDescriptor.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}