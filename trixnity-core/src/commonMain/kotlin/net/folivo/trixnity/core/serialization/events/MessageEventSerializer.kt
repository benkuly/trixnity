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
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.UnknownMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class MessageEventSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out MessageEventContent>>,
) : KSerializer<MessageEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomEventSerializer")

    private val eventsContentLookupByType = messageEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): MessageEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        val isRedacted = jsonObj["unsigned"]?.jsonObject?.get("redacted_because") != null
        val redacts = jsonObj["redacts"]?.jsonPrimitive?.content // TODO hopefully a new spec removes this hack
        requireNotNull(type)
        val contentSerializer =
            if (!isRedacted)
                eventsContentLookupByType[type]
                    ?: UnknownEventContentSerializer(UnknownMessageEventContent.serializer(), type)
            else RedactedEventContentSerializer(RedactedMessageEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(
                MessageEvent.serializer(
                    if (redacts == null) contentSerializer
                    else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
                ), jsonObj
            )
        } catch (error: SerializationException) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                MessageEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownMessageEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: MessageEvent<*>) {
        val content = value.content
        val type: String
        val serializer: KSerializer<out MessageEventContent>
        when (content) {
            is UnknownMessageEventContent -> {
                type = content.eventType
                serializer = UnknownEventContentSerializer(UnknownMessageEventContent.serializer(), type)
            }
            is RedactedMessageEventContent -> {
                type = content.eventType
                serializer = RedactedEventContentSerializer(RedactedMessageEventContent.serializer(), type)
            }
            else -> {
                val contentDescriptor = messageEventContentSerializers.find { it.kClass.isInstance(value.content) }
                requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
                type = contentDescriptor.type
                serializer = contentDescriptor.serializer
            }
        }
        require(encoder is JsonEncoder)

        val addFields = mutableListOf("type" to type)
        if (content is RedactionEventContent) addFields.add("redacts" to content.redacts.full)
        val contentSerializer =
            if (content is RedactionEventContent)
                HideFieldsSerializer(serializer, "redacts")
            else serializer

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                MessageEvent.serializer(contentSerializer) as KSerializer<MessageEvent<*>>,
                *addFields.toTypedArray()
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}