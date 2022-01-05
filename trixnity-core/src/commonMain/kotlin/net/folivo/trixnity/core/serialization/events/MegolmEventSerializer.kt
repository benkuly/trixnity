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
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class MegolmEventSerializer(
    private val roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
) : KSerializer<MegolmEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MegolmEventSerializer")

    private val eventsContentLookupByType = roomEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): MegolmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type]
            ?: UnknownEventContentSerializer(UnknownRoomEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(MegolmEvent.serializer(contentSerializer), jsonObj)
        } catch (error: SerializationException) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                MegolmEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownRoomEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: MegolmEvent<*>) {
        val content = value.content
        if (content is UnknownRoomEventContent) throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentSerializerMapping = roomEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentSerializerMapping) { "event content type ${value.content::class} must be registered" }

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                MegolmEvent.serializer(contentSerializerMapping.serializer) as KSerializer<MegolmEvent<*>>,
                "type" to contentSerializerMapping.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}