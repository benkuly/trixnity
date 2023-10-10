package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

class EventContentSerializer<T : EventContent>(
    private val type: String,
    private val baseSerializer: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor = buildClassSerialDescriptor("EventContentSerializer-$type")

    override fun deserialize(decoder: Decoder): T {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            baseSerializer, decoder.decodeJsonElement()
        ) {
            log.warn(it) { "could not deserialize event content of type $type" }
            @Suppress("UNCHECKED_CAST")
            UnknownEventContentSerializer(type) as KSerializer<T>
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(baseSerializer, value)))
    }
}

// TODO hopefully a new spec removes the m.new_content (m.replace) hack
class MessageEventContentSerializer<T : MessageEventContent>(
    private val type: String,
    baseSerializer: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor = buildClassSerialDescriptor("MessageEventContentSerializer-$type")

    private val serializer = NewContentToRelatesToSerializer(type, baseSerializer)

    override fun deserialize(decoder: Decoder): T {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            serializer,
            decoder.decodeJsonElement(),
            {
                @Suppress("UNCHECKED_CAST")
                RedactedEventContentSerializer(type) as KSerializer<out T>
            },
        ) {
            log.warn(it) { "could not deserialize event content of type $type" }
            @Suppress("UNCHECKED_CAST")
            UnknownEventContentSerializer(type) as KSerializer<out T>
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }

    class NewContentToRelatesToSerializer<T : MessageEventContent>(
        val type: String,
        baseSerializer: KSerializer<T>
    ) : JsonTransformingSerializer<T>(baseSerializer) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            if (element !is JsonObject) return element
            val newContent = element["m.new_content"] ?: return element
            val relatesTo = element["m.relates_to"] ?: return element
            if (relatesTo !is JsonObject || newContent !is JsonObject) return element
            return JsonObject(buildMap {
                putAll(element)
                put("m.relates_to", JsonObject(buildMap {
                    putAll(relatesTo)
                    put("m.new_content", JsonObject(buildMap {
                        putAll(newContent)
                        put("type", JsonPrimitive(type)) // trigger the fallback (see above)
                    }))
                }))
            })
        }

        override fun transformSerialize(element: JsonElement): JsonElement {
            if (element !is JsonObject) return element
            val relatesTo = element["m.relates_to"] ?: return element
            if (relatesTo !is JsonObject) return element
            val newContent = relatesTo["m.new_content"] ?: return element
            return JsonObject(buildMap {
                putAll(element)
                if (type != "m.room.encrypted") put("m.new_content", newContent)
                put("m.relates_to", JsonObject(buildMap {
                    putAll(relatesTo)
                    remove("m.new_content")
                }))
            })
        }
    }
}

internal class ContextualMessageEventContentSerializer(
    private val mappings: Set<MessageEventContentSerializerMapping>,
) : KSerializer<MessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("ContextualMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): MessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content // this is a fallback (e.g. for RelatesTo)
            ?: throw SerializationException("type must not be null for deserializing MessageEventContent")

        val serializer = mappings.contentSerializer(type)
        return decoder.json.decodeFromJsonElement(serializer, jsonObject)
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        require(encoder is JsonEncoder)
        val serializer = mappings.contentSerializer(value)
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}

class StateEventContentSerializer<T : StateEventContent>(
    private val type: String,
    private val baseSerializer: KSerializer<T>,
) : KSerializer<T> {
    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer-$type")

    override fun deserialize(decoder: Decoder): T {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            baseSerializer,
            decoder.decodeJsonElement(),
            {
                @Suppress("UNCHECKED_CAST")
                RedactedEventContentSerializer(type) as KSerializer<out T>
            },
        ) {
            log.warn(it) { "could not deserialize event content of type $type" }
            @Suppress("UNCHECKED_CAST")
            UnknownEventContentSerializer(type) as KSerializer<out T>
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(baseSerializer, value)))
    }
}
