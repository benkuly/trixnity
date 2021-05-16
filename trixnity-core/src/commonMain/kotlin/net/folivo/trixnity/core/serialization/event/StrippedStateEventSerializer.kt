package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.Event.StrippedStateEvent
import net.folivo.trixnity.core.model.events.RedactedRoomEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

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
        val content = jsonObj["content"]
        val contentSerializer =
            if (content != null && content.jsonObject.isNotEmpty())
                eventsContentLookupByType[type]
                    ?: UnknownEventContentSerializer(UnknownStateEventContent.serializer(), type)
            else RedactedEventContentSerializer(RedactedRoomEventContent.serializer(), type)
        return decoder.json.decodeFromJsonElement(
            StrippedStateEvent.serializer(contentSerializer),
            jsonObj
        )
    }

    override fun serialize(encoder: Encoder, value: StrippedStateEvent<*>) {
        val content = value.content
        if (content is UnknownStateEventContent || value.content is RedactedStateEventContent)
            throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentDescriptor = stateEventContentSerializers.find { it.kClass.isInstance(content) }
        requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            AddFieldsSerializer(
                StrippedStateEvent.serializer(contentDescriptor.serializer) as KSerializer<StrippedStateEvent<*>>,
                "type" to contentDescriptor.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}