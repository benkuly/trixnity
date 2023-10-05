package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.Event.MessageEvent
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
                        val content = jsonObject["content"]?.jsonObject
                        return if (redacts != null && content != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put("content", JsonObject(
                                    buildMap {
                                        putAll(content)
                                        put("redacts", redacts)
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
                        val redacts = jsonObject["content"]?.jsonObject?.get("redacts")
                        return if (redacts != null)
                            JsonObject(buildMap {
                                putAll(jsonObject)
                                put("redacts", redacts)
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