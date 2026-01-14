package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentStateDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

interface RoomVersionStore {
    fun getRoomVersion(roomId: RoomId): String
    fun setRoomVersion(pdu: PersistentDataUnit.PersistentStateDataUnit<*>, roomVersion: String)
}

class PersistentStateDataUnitSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
    private val roomVersionStore: RoomVersionStore,
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
    private val mappingV12 = RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { PersistentStateDataUnitV12.serializer(it.serializer) },
        eventSerializer = {
            AddFieldsSerializer(
                PersistentStateDataUnitV12.serializer(it.serializer),
                "type" to it.type
            )
        },
        unknownEventSerializer = { PersistentStateDataUnitV12.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentStateDataUnitV12.serializer(RedactedEventContentSerializer(it)) },
    )

    override fun deserialize(decoder: Decoder): PersistentDataUnit.PersistentStateDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type =
            (jsonObj["type"] as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("type must not be null")
        val roomId = (jsonObj["room_id"] as? JsonPrimitive)?.contentOrNull
        val isCreateEvent = type == "m.room.create"
        val roomVersion =
            when {
                isCreateEvent -> {
                    ((jsonObj["content"] as JsonObject?)?.get("room_version") as? JsonPrimitive)?.contentOrNull ?: "1"
                }

                roomId == null -> throw SerializationException("roomId must not be null")
                else -> roomVersionStore.getRoomVersion(RoomId(roomId))
            }
        val pdu = when (roomVersion) {
            "1", "2" -> decoder.json.decodeFromJsonElement(mappingV1[type], jsonObj)
            "3", "4", "5", "6", "7", "8", "9", "10", "11" ->
                decoder.json.decodeFromJsonElement(mappingV3[type], jsonObj)

            "12" -> decoder.json.decodeFromJsonElement(mappingV12[type], jsonObj)


            else -> throw SerializationException("room version $roomVersion not supported")
        }
        if (isCreateEvent) roomVersionStore.setRoomVersion(pdu, roomVersion)
        return pdu
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

                is PersistentStateDataUnitV12 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV12[content].serializer as KSerializer<PersistentStateDataUnitV12<*>>,
                        value
                    )
            }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}