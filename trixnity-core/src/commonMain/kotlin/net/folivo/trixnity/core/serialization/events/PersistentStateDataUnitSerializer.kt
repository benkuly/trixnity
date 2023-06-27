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
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentStateDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class PersistentStateDataUnitSerializer(
    private val stateEventContentSerializers: Set<SerializerMapping<out StateEventContent>>,
    private val stateEventContentSerializer: StateEventContentSerializer,
    private val getRoomVersion: (RoomId) -> String,
) : KSerializer<PersistentDataUnit.PersistentStateDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PersistentStateDataUnitSerializer")

    override fun deserialize(decoder: Decoder): PersistentDataUnit.PersistentStateDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val isFullyRedacted = jsonObj["content"]?.jsonObject?.isEmpty() == true
        val contentSerializer =
            StateEventContentSerializer.withRedaction(stateEventContentSerializers, type, isFullyRedacted)
        val roomId = jsonObj["room_id"]?.jsonPrimitive?.content
        requireNotNull(roomId)
        return when (val roomVersion = getRoomVersion(RoomId(roomId))) {
            "1", "2" -> {
                decoder.json.tryDeserializeOrElse(PersistentStateDataUnitV1.serializer(contentSerializer), jsonObj) {
                    log.warn(it) { "could not deserialize event: $jsonObj" }
                    PersistentStateDataUnitV1.serializer(UnknownMessageEventContentSerializer(type))
                }
            }

            "3", "4", "5", "6", "7", "8", "9" -> {
                decoder.json.tryDeserializeOrElse(PersistentStateDataUnitV3.serializer(contentSerializer), jsonObj) {
                    log.warn(it) { "could not deserialize event: $jsonObj" }
                    PersistentStateDataUnitV3.serializer(UnknownMessageEventContentSerializer(type))
                }
            }

            else -> throw SerializationException("room version $roomVersion not supported")
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentDataUnit.PersistentStateDataUnit<*>) {
        require(encoder is JsonEncoder)
        val type = stateEventContentSerializers.contentType(value.content)

        val addFields = mutableListOf("type" to type)

        val jsonElement =
            when (value) {
                is PersistentStateDataUnitV1 -> encoder.json.encodeToJsonElement(
                    @Suppress("UNCHECKED_CAST")
                    (AddFieldsSerializer(
                        PersistentStateDataUnitV1.serializer(stateEventContentSerializer) as KSerializer<PersistentStateDataUnitV1<*>>,
                        *addFields.toTypedArray()
                    )), value
                )

                is PersistentStateDataUnitV3 -> encoder.json.encodeToJsonElement(
                    @Suppress("UNCHECKED_CAST")
                    (AddFieldsSerializer(
                        PersistentStateDataUnitV3.serializer(stateEventContentSerializer) as KSerializer<PersistentStateDataUnitV3<*>>,
                        *addFields.toTypedArray()
                    )), value
                )
            }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}