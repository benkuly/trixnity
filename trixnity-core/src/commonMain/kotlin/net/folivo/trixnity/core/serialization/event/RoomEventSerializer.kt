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
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

class RoomEventSerializer(
    private val roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
) : KSerializer<RoomEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomEventSerializer")

    private val eventsContentLookupByType = roomEventContentSerializers.map { Pair(it.type, it.serializer) }.toMap()

    override fun deserialize(decoder: Decoder): RoomEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer =
            eventsContentLookupByType[type] ?: UnknownEventContentSerializer(UnknownRoomEventContent.serializer(), type)
        return decoder.json.decodeFromJsonElement(
            HideDiscriminatorSerializer(
                RoomEvent.serializer(contentSerializer),
                "type",
                type
            ), jsonObj
        )
    }

    override fun serialize(encoder: Encoder, value: RoomEvent<*>) {
        if (value.content is UnknownRoomEventContent) throw IllegalArgumentException("${UnknownRoomEventContent::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentDescriptor = roomEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentDescriptor, { "event content type ${value.content::class} must be registered" })

        println(contentDescriptor.serializer)
        val jsonElement = encoder.json.encodeToJsonElement(
            HideDiscriminatorSerializer(
                RoomEvent.serializer(contentDescriptor.serializer) as KSerializer<RoomEvent<*>>,
                "type",
                contentDescriptor.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}