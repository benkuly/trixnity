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
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentStateDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

class PersistentStateDataUnitSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
    private val getRoomVersion: (RoomId) -> String,
) : KSerializer<PersistentDataUnit.PersistentStateDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PersistentStateDataUnitSerializer")
    private val mappingV1 = RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { PersistentStateDataUnitV1.serializer(it.serializer) },
        eventSerializer = {
            AddFieldsSerializer(
                PersistentStateDataUnitV1.serializer(it.serializer),
                "type" to it.type
            )
        },
        unknownEventSerializer = { PersistentStateDataUnitV1.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentStateDataUnitV1.serializer(RedactedEventContentSerializer(it)) },
    )
    private val mappingV3 = RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { PersistentStateDataUnitV3.serializer(it.serializer) },
        eventSerializer = {
            AddFieldsSerializer(
                PersistentStateDataUnitV3.serializer(it.serializer),
                "type" to it.type
            )
        },
        unknownEventSerializer = { PersistentStateDataUnitV3.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentStateDataUnitV3.serializer(RedactedEventContentSerializer(it)) },
    )

    override fun deserialize(decoder: Decoder): PersistentDataUnit.PersistentStateDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val roomId = jsonObj["room_id"]?.jsonPrimitive?.content
        requireNotNull(roomId)
        return when (val roomVersion = getRoomVersion(RoomId(roomId))) {
            "1", "2" -> decoder.json.decodeFromJsonElement(mappingV1[type], jsonObj)
            "3", "4", "5", "6", "7", "8", "9" -> decoder.json.decodeFromJsonElement(mappingV3[type], jsonObj)

            else -> throw SerializationException("room version $roomVersion not supported")
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentDataUnit.PersistentStateDataUnit<*>) {
        require(encoder is JsonEncoder)
        val content = value.content

        val jsonElement =
            when (value) {
                is PersistentStateDataUnitV1 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV1[content].serializer as KSerializer<PersistentStateDataUnitV1<*>>,
                        value
                    )

                is PersistentStateDataUnitV3 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV3[content].serializer as KSerializer<PersistentStateDataUnitV3<*>>,
                        value
                    )
            }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}