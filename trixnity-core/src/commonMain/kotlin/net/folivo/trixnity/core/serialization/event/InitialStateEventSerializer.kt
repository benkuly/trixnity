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
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.InitialStateEvent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class InitialStateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<InitialStateEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InitialStateEventSerializer")

    private val eventsContentLookupByType = stateEventContentSerializers.map { Pair(it.type, it.serializer) }.toMap()

    override fun deserialize(decoder: Decoder): InitialStateEvent<*> {
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
                InitialStateEvent.serializer(contentSerializer),
                jsonObj
            )
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                InitialStateEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownStateEventContent.serializer(),
                        type
                    )
                ),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: InitialStateEvent<*>) {
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
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            AddFieldsSerializer(
                InitialStateEvent.serializer(serializer) as KSerializer<InitialStateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}