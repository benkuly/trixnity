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
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class RoomAccountDataEventSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out RoomAccountDataEventContent>>,
) : KSerializer<Event.RoomAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomAccountDataEventSerializer")

    private val eventsContentLookupByType = messageEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): Event.RoomAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val mapping = eventsContentLookupByType.entries.find { type.startsWith(it.key) }
        val contentSerializer = mapping?.value
            ?: UnknownEventContentSerializer(UnknownRoomAccountDataEventContent.serializer(), type)
        return try {
            val key = if (mapping != null && mapping.key != type) type.substringAfter(mapping.key) else ""
            decoder.json.decodeFromJsonElement(
                AddFieldsSerializer(
                    Event.RoomAccountDataEvent.serializer(contentSerializer),
                    "key" to key
                ), jsonObj
            )
        } catch (error: Exception) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                Event.RoomAccountDataEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownRoomAccountDataEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Event.RoomAccountDataEvent<*>) {
        val content = value.content
        val type: String
        val serializer: KSerializer<out RoomAccountDataEventContent>

        when (content) {
            is UnknownRoomAccountDataEventContent -> {
                type = content.eventType
                serializer = UnknownEventContentSerializer(UnknownRoomAccountDataEventContent.serializer(), type)
            }
            else -> {
                val contentDescriptor = messageEventContentSerializers.find { it.kClass.isInstance(content) }
                requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
                type = contentDescriptor.type
                serializer = contentDescriptor.serializer
            }
        }
        require(encoder is JsonEncoder)
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