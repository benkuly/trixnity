package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class RoomAccountDataEventSerializer(
    private val roomAccountDataEventContentSerializers: Set<EventContentSerializerMapping<RoomAccountDataEventContent>>,
) : KSerializer<RoomAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomAccountDataEventSerializer")
    private val mappings = EventContentToEventSerializerMappings(
        baseMapping = roomAccountDataEventContentSerializers,
        eventDeserializer = { RoomAccountDataEvent.serializer(it.serializer) },
        unknownEventSerializer = { RoomAccountDataEvent.serializer(UnknownEventContentSerializer(it)) },
        typeField = null,
    )

    override fun deserialize(decoder: Decoder): RoomAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = (jsonObj["type"] as? JsonPrimitive)?.content ?: throw SerializationException("type must not be null")
        val mappingType = roomAccountDataEventContentSerializers.find { type.startsWith(it.type) }?.type
        val baseSerializer = mappings[mappingType ?: type]
        val key = if (mappingType != null && mappingType != type) type.substringAfter(mappingType) else ""
        return decoder.json.tryDeserializeOrElse(
            AddFieldsSerializer(
                baseSerializer,
                "key" to key
            ), jsonObj
        ) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            @Suppress("UNCHECKED_CAST")
            RoomAccountDataEvent.serializer(UnknownEventContentSerializer(type)) as KSerializer<RoomAccountDataEvent<RoomAccountDataEventContent>>
        }
    }

    override fun serialize(encoder: Encoder, value: RoomAccountDataEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, baseSerializer) = mappings[value.content]
        val jsonElement = encoder.json.encodeToJsonElement(
            (HideFieldsSerializer(
                AddFieldsSerializer(
                    @Suppress("UNCHECKED_CAST") (baseSerializer as KSerializer<RoomAccountDataEvent<*>>),
                    "type" to type + value.key
                ), "key"
            )), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}