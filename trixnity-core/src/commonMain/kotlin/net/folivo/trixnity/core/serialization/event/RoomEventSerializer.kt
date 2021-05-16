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
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.RedactedRoomEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

class RoomEventSerializer(
    private val roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
) : KSerializer<RoomEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomEventSerializer")

    private val eventsContentLookupByType = roomEventContentSerializers.map { Pair(it.type, it.serializer) }.toMap()

    override fun deserialize(decoder: Decoder): RoomEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        val redacts = jsonObj["redacts"]?.jsonPrimitive?.content // TODO hopefully a new spec removes this hack
        requireNotNull(type)
        val content = jsonObj["content"]
        val contentSerializer =
            if (content != null && content.jsonObject.isNotEmpty())
                eventsContentLookupByType[type]
                    ?: UnknownEventContentSerializer(UnknownRoomEventContent.serializer(), type)
            else RedactedEventContentSerializer(RedactedRoomEventContent.serializer(), type)
        return decoder.json.decodeFromJsonElement(
            RoomEvent.serializer(
                if (redacts == null) contentSerializer
                else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
            ), jsonObj
        )
    }

    override fun serialize(encoder: Encoder, value: RoomEvent<*>) {
        val content = value.content
        if (content is UnknownRoomEventContent || content is RedactedRoomEventContent) throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentSerializerMapping = roomEventContentSerializers.find { it.kClass.isInstance(value.content) }
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
                RoomEvent.serializer(contentSerializer) as KSerializer<RoomEvent<*>>,
                *addFields.toTypedArray()
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}