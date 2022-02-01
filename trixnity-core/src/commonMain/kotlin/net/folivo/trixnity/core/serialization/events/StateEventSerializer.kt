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
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class StateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
) : KSerializer<StateEvent<*>> {
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
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                StateEvent.serializer(UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: StateEvent<*>) {
        val content = value.content
        val type: String
        val serializer: KSerializer<out StateEventContent>
        when (content) {
            is UnknownStateEventContent -> {
                type = content.eventType
                serializer = UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)
            }
            is RedactedStateEventContent -> {
                type = content.eventType
                serializer = RedactedEventContentSerializer(RedactedStateEventContent.serializer(), type)
            }
            else -> {
                val contentDescriptor = stateEventContentSerializers.find { it.kClass.isInstance(content) }
                requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
                type = contentDescriptor.type
                serializer = contentDescriptor.serializer
            }
        }
        require(encoder is JsonEncoder)
        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                StateEvent.serializer(serializer) as KSerializer<StateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}