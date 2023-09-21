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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentMessageDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentMessageDataUnitV3
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentMessageDataUnit
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class PersistentMessageDataUnitSerializer(
    private val messageEventContentSerializers: Set<SerializerMapping<out MessageEventContent>>,
    private val messageEventContentSerializer: MessageEventContentSerializer,
    private val getRoomVersion: (RoomId) -> String,
) : KSerializer<PersistentMessageDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PersistentMessageDataUnitSerializer")

    override fun deserialize(decoder: Decoder): PersistentMessageDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val redacts = jsonObj["redacts"]?.jsonPrimitive?.content // TODO hopefully a new spec removes this hack
        val contentSerializer = MessageEventContentSerializer(messageEventContentSerializers, type)
        val roomId = jsonObj["room_id"]?.jsonPrimitive?.content
        requireNotNull(roomId)
        return when (val roomVersion = getRoomVersion(RoomId(roomId))) {
            "1", "2" -> {
                decoder.json.decodeFromJsonElement(
                    PersistentMessageDataUnitV1.serializer(
                        if (redacts == null) contentSerializer
                        else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
                    ), jsonObj
                )
            }

            "3", "4", "5", "6", "7", "8", "9" -> {
                decoder.json.decodeFromJsonElement(
                    PersistentMessageDataUnitV3.serializer(
                        if (redacts == null) contentSerializer
                        else AddFieldsSerializer(contentSerializer, "redacts" to redacts)
                    ), jsonObj
                )
            }

            else -> throw SerializationException("room version $roomVersion not supported")
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentMessageDataUnit<*>) {
        require(encoder is JsonEncoder)
        val content = value.content
        val type = messageEventContentSerializers.contentType(content)

        val addFields = mutableListOf("type" to type)
        if (content is RedactionEventContent) addFields.add("redacts" to content.redacts.full)
        val contentSerializer =
            if (content is RedactionEventContent)
                HideFieldsSerializer(messageEventContentSerializer, "redacts")
            else messageEventContentSerializer

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
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}