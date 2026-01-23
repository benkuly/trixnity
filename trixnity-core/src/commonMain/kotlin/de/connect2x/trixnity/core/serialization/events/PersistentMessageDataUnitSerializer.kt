package de.connect2x.trixnity.core.serialization.events

import de.connect2x.lognity.api.logger.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentMessageDataUnitV1
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV12.PersistentMessageDataUnitV12
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentMessageDataUnitV3
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentMessageDataUnit
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.serialization.AddFieldsSerializer
import de.connect2x.trixnity.core.serialization.canonicalJson

private val log =
    Logger("de.connect2x.trixnity.core.serialization.events.PersistentMessageDataUnit")

class PersistentMessageDataUnitSerializer(
    messageEventContentSerializers: Set<EventContentSerializerMapping<MessageEventContent>>,
    private val roomVersionStore: RoomVersionStore,
) : KSerializer<PersistentMessageDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PersistentMessageDataUnit")
    private val mappingV1 = RoomEventContentToEventSerializerMappings(
        baseMapping = messageEventContentSerializers,
        eventDeserializer = {
            val baseSerializer = PersistentMessageDataUnitV1.serializer(it.serializer)
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV1<MessageEventContent>>(baseSerializer) {
                    override fun transformDeserialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = jsonObject["redacts"]
                        val content = jsonObject["content"] as? JsonObject
                        return if (redacts != null && content != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put(
                                    "content", JsonObject(
                                        buildMap {
                                            put("redacts", redacts)
                                            putAll(content)
                                        }
                                    ))
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        eventSerializer = {
            val baseSerializer = AddFieldsSerializer(
                PersistentMessageDataUnitV1.serializer(it.serializer),
                "type" to it.type
            )
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV1<MessageEventContent>>(baseSerializer) {
                    override fun transformSerialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = (jsonObject["content"] as? JsonObject)?.get("redacts")
                        return if (redacts != null)
                            JsonObject(buildMap {
                                put("redacts", redacts)
                                putAll(jsonObject)
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        unknownEventSerializer = { PersistentMessageDataUnitV1.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentMessageDataUnitV1.serializer(RedactedEventContentSerializer(it)) },
    )
    private val mappingV3 = RoomEventContentToEventSerializerMappings(
        baseMapping = messageEventContentSerializers,
        eventDeserializer = {
            val baseSerializer = PersistentMessageDataUnitV3.serializer(it.serializer)
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV3<MessageEventContent>>(baseSerializer) {
                    override fun transformDeserialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = jsonObject["redacts"]
                        val content = jsonObject["content"] as? JsonObject
                        return if (redacts != null && content != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put(
                                    "content", JsonObject(
                                        buildMap {
                                            put("redacts", redacts)
                                            putAll(content)
                                        }
                                    ))
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        eventSerializer = {
            val baseSerializer = AddFieldsSerializer(
                PersistentMessageDataUnitV3.serializer(it.serializer),
                "type" to it.type
            )
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV3<MessageEventContent>>(baseSerializer) {
                    override fun transformSerialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = (jsonObject["content"] as? JsonObject)?.get("redacts")
                        return if (redacts != null)
                            JsonObject(buildMap {
                                put("redacts", redacts)
                                putAll(jsonObject)
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        unknownEventSerializer = { PersistentMessageDataUnitV3.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentMessageDataUnitV3.serializer(RedactedEventContentSerializer(it)) },
    )
    private val mappingV12 = RoomEventContentToEventSerializerMappings(
        baseMapping = messageEventContentSerializers,
        eventDeserializer = {
            val baseSerializer = PersistentMessageDataUnitV12.serializer(it.serializer)
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV12<MessageEventContent>>(baseSerializer) {
                    override fun transformDeserialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = jsonObject["redacts"]
                        val content = jsonObject["content"] as? JsonObject
                        return if (redacts != null && content != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put(
                                    "content", JsonObject(
                                        buildMap {
                                            put("redacts", redacts)
                                            putAll(content)
                                        }
                                    ))
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        eventSerializer = {
            val baseSerializer = AddFieldsSerializer(
                PersistentMessageDataUnitV12.serializer(it.serializer),
                "type" to it.type
            )
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<PersistentMessageDataUnitV12<MessageEventContent>>(baseSerializer) {
                    override fun transformSerialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = (jsonObject["content"] as? JsonObject)?.get("redacts")
                        return if (redacts != null)
                            JsonObject(buildMap {
                                put("redacts", redacts)
                                putAll(jsonObject)
                            })
                        else element
                    }
                }
            else baseSerializer
        },
        unknownEventSerializer = { PersistentMessageDataUnitV12.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { PersistentMessageDataUnitV12.serializer(RedactedEventContentSerializer(it)) },
    )

    override fun deserialize(decoder: Decoder): PersistentMessageDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type =
            (jsonObj["type"] as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("type must not be null")
        val roomId = (jsonObj["room_id"] as? JsonPrimitive)?.contentOrNull
        requireNotNull(roomId)
        return when (val roomVersion = roomVersionStore.getRoomVersion(RoomId(roomId))) {
            "1", "2" -> decoder.json.decodeFromJsonElement(mappingV1[type], jsonObj)
            "3", "4", "5", "6", "7", "8", "9", "10", "11" ->
                decoder.json.decodeFromJsonElement(mappingV3[type], jsonObj)

            "12" -> decoder.json.decodeFromJsonElement(mappingV12[type], jsonObj)

            else -> throw SerializationException("room version $roomVersion not supported")
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentMessageDataUnit<*>) {
        require(encoder is JsonEncoder)
        val content = value.content
        val jsonElement =
            when (value) {
                is PersistentMessageDataUnitV1 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV1[content].serializer as KSerializer<PersistentMessageDataUnitV1<*>>,
                        value
                    )

                is PersistentMessageDataUnitV3 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV3[content].serializer as KSerializer<PersistentMessageDataUnitV3<*>>,
                        value
                    )

                is PersistentMessageDataUnitV12 ->
                    @Suppress("UNCHECKED_CAST")
                    encoder.json.encodeToJsonElement(
                        mappingV12[content].serializer as KSerializer<PersistentMessageDataUnitV12<*>>,
                        value
                    )
            }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}