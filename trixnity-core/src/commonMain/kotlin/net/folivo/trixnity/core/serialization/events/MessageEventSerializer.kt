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
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class MessageEventSerializer(
    private val messageEventContentSerializers: Set<SerializerMapping<out MessageEventContent>>,
) : KSerializer<MessageEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageEventSerializer")

    override fun deserialize(decoder: Decoder): MessageEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val isRedacted = jsonObj["unsigned"]?.jsonObject?.get("redacted_because") != null
        val redacts = jsonObj["redacts"]?.jsonPrimitive?.content // TODO hopefully a new spec removes this hack
        val contentSerializer = messageEventContentSerializers.contentDeserializer(type, isRedacted)
        return decoder.json.tryDeserializeOrElse(
            MessageEvent.serializer(
                if (redacts == null) contentSerializer
                else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
            ), jsonObj
        ) {
            log.warn(it) { "could not deserialize event of type $type" }
            MessageEvent.serializer(UnknownMessageEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: MessageEvent<*>) {
        require(encoder is JsonEncoder)
        val content = value.content
        val (type, serializer) = messageEventContentSerializers.contentSerializer(content)

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