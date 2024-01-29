package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

class MessageEventSerializer(
    messageEventContentSerializers: Set<EventContentSerializerMapping<MessageEventContent>>,
) : BaseEventSerializer<MessageEventContent, MessageEvent<*>>(
    "MessageEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = messageEventContentSerializers,
        eventDeserializer = {
            val baseSerializer = MessageEvent.serializer(it.serializer)
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<MessageEvent<MessageEventContent>>(baseSerializer) {
                    override fun transformDeserialize(element: JsonElement): JsonElement {
                        val jsonObject = element.jsonObject
                        val redacts = jsonObject["redacts"]
                        val content = jsonObject["content"] as? JsonObject
                        return if (redacts != null && content != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put("content", JsonObject(
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
                MessageEvent.serializer(it.serializer),
                "type" to it.type
            )
            if (it.kClass == RedactionEventContent::class)
                object : JsonTransformingSerializer<MessageEvent<MessageEventContent>>(baseSerializer) {
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
        unknownEventSerializer = { MessageEvent.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { MessageEvent.serializer(RedactedEventContentSerializer(it)) },
    )
)