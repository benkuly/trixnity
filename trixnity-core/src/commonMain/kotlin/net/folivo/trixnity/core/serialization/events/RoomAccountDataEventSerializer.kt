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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class RoomAccountDataEventSerializer(
    private val roomAccountDataEventContentSerializers: Set<EventContentSerializerMapping<out RoomAccountDataEventContent>>,
) : KSerializer<Event.RoomAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomAccountDataEventSerializer")

    override fun deserialize(decoder: Decoder): Event.RoomAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val mappingType = roomAccountDataEventContentSerializers.firstOrNull { type.startsWith(it.type) }?.type
        val contentSerializer = roomAccountDataEventContentSerializers.contentDeserializer(type)
        return try {
            val key = if (mappingType != null && mappingType != type) type.substringAfter(mappingType) else ""
            decoder.json.decodeFromJsonElement(
                AddFieldsSerializer(
                    Event.RoomAccountDataEvent.serializer(contentSerializer),
                    "key" to key
                ), jsonObj
            )
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                Event.RoomAccountDataEvent.serializer(UnknownRoomAccountDataEventContentSerializer(type)), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Event.RoomAccountDataEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = roomAccountDataEventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            (HideFieldsSerializer(
                AddFieldsSerializer(
                    Event.RoomAccountDataEvent.serializer(serializer) as KSerializer<Event.RoomAccountDataEvent<*>>,
                    "type" to type + value.key
                ), "key"
            )), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}