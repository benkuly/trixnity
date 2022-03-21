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
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class MegolmEventSerializer(
    private val roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
) : KSerializer<MegolmEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MegolmEventSerializer")

    override fun deserialize(decoder: Decoder): MegolmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = roomEventContentSerializers.contentDeserializer(type)
        return try {
            decoder.json.decodeFromJsonElement(MegolmEvent.serializer(contentSerializer), jsonObj)
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                MegolmEvent.serializer(UnknownRoomEventContentSerializer(type)), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: MegolmEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = roomEventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                MegolmEvent.serializer(serializer) as KSerializer<MegolmEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}