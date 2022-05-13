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
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentMessageDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentMessageDataUnitV3
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentMessageDataUnit
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class PersistentMessageDataUnitSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out MessageEventContent>>,
    private val getRoomVersion: (RoomId) -> String,
) : KSerializer<PersistentMessageDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PersistentMessageDataUnitSerializer")

    override fun deserialize(decoder: Decoder): PersistentMessageDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        val isRedacted = jsonObj["content"]?.jsonObject?.isEmpty() == true
        val redacts = jsonObj["redacts"]?.jsonPrimitive?.content // TODO hopefully a new spec removes this hack
        requireNotNull(type)
        val contentSerializer = messageEventContentSerializers.contentDeserializer(type, isRedacted)
        val roomId = jsonObj["room_id"]?.jsonPrimitive?.content
        requireNotNull(roomId)
        return when (val roomVersion = getRoomVersion(RoomId(roomId))) {
            "1", "2" -> {
                decoder.json.tryDeserializeOrElse(
                    PersistentMessageDataUnitV1.serializer(
                        if (redacts == null) contentSerializer
                        else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
                    ), jsonObj
                ) {
                    log.warn(it) { "could not deserialize pdu of type $type" }
                    PersistentMessageDataUnitV1.serializer(UnknownMessageEventContentSerializer(type))
                }
            }
            "3", "4", "5", "6", "7", "8", "9" -> {
                decoder.json.tryDeserializeOrElse(
                    PersistentMessageDataUnitV3.serializer(
                        if (redacts == null) contentSerializer
                        else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
                    ), jsonObj
                ) {
                    log.warn(it) { "could not deserialize pdu of type $type" }
                    PersistentMessageDataUnitV3.serializer(UnknownMessageEventContentSerializer(type))
                }
            }
            else -> throw SerializationException("room version $roomVersion not supported")
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentMessageDataUnit<*>) {
        require(encoder is JsonEncoder)
        val content = value.content
        val (type, serializer) = messageEventContentSerializers.contentSerializer(content)

        val addFields = mutableListOf("type" to type)
        if (content is RedactionEventContent) addFields.add("redacts" to content.redacts.full)
        val contentSerializer =
            if (content is RedactionEventContent)
                HideFieldsSerializer(serializer, "redacts")
            else serializer

        val jsonElement =
            when (value) {
                is PersistentMessageDataUnitV1 -> encoder.json.encodeToJsonElement(
                    @Suppress("UNCHECKED_CAST")
                    (AddFieldsSerializer(
                        PersistentMessageDataUnitV1.serializer(contentSerializer) as KSerializer<PersistentMessageDataUnitV1<*>>,
                        *addFields.toTypedArray()
                    )), value
                )
                is PersistentMessageDataUnitV3 -> encoder.json.encodeToJsonElement(
                    @Suppress("UNCHECKED_CAST")
                    (AddFieldsSerializer(
                        PersistentMessageDataUnitV3.serializer(contentSerializer) as KSerializer<PersistentMessageDataUnitV3<*>>,
                        *addFields.toTypedArray()
                    )), value
                )
            }
        encoder.encodeJsonElement(jsonElement)
    }
}