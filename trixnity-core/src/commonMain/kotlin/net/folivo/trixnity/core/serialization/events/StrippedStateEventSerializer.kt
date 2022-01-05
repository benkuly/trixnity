package net.folivo.trixnity.core.serialization.events

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
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event.StrippedStateEvent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class StrippedStateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
) : KSerializer<StrippedStateEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StrippedStateEventSerializer")

    private val eventsContentLookupByType = stateEventContentSerializers.map { Pair(it.type, it.serializer) }.toMap()

    override fun deserialize(decoder: Decoder): StrippedStateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val isRedacted = jsonObj["unsigned"]?.jsonObject?.get("redacted_because") != null
        val contentSerializer =
            if (!isRedacted)
                eventsContentLookupByType[type]
                    ?: UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)
            else RedactedEventContentSerializer(RedactedMessageEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(
                StrippedStateEvent.serializer(contentSerializer),
                jsonObj
            )
        } catch (error: SerializationException) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                StrippedStateEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownStateEventContent.serializer(),
                        type
                    )
                ),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: StrippedStateEvent<*>) {
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
                StrippedStateEvent.serializer(serializer) as KSerializer<StrippedStateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}