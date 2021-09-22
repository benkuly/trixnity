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
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.UnknownMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class MessageEventSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out MessageEventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<MessageEvent<*>> {
    private val log = newLogger(loggerFactory)
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
            log.warning(error) { "could not deserialize event" }
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
        if (content is UnknownMessageEventContent || content is RedactedMessageEventContent) throw IllegalArgumentException(
            "${content::class.simpleName} should never be serialized"
        )
        require(encoder is JsonEncoder)
        val contentSerializerMapping = messageEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentSerializerMapping) { "event content type ${value.content::class} must be registered" }

        val addFields = mutableListOf("type" to contentSerializerMapping.type)
        if (content is RedactionEventContent) addFields.add("redacts" to content.redacts.full)
        val contentSerializer =
            if (content is RedactionEventContent)
                HideFieldsSerializer(contentSerializerMapping.serializer, "redacts")
            else contentSerializerMapping.serializer

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            AddFieldsSerializer(
                MessageEvent.serializer(contentSerializer) as KSerializer<MessageEvent<*>>,
                *addFields.toTypedArray()
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}