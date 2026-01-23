package de.connect2x.trixnity.core.serialization.events

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.serialization.canonicalJson

private val log = Logger("de.connect2x.trixnity.core.serialization.events.EventContent")

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
class MessageEventContentSerializer(
    private val type: String,
    baseSerializer: KSerializer<out MessageEventContent>,
) : KSerializer<MessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("MessageEventContentSerializer-$type")

    @Suppress("UNCHECKED_CAST")
    private val serializer = NewContentToRelatesToSerializer(type, baseSerializer as KSerializer<MessageEventContent>)

    override fun deserialize(decoder: Decoder): MessageEventContent {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            serializer,
            decoder.decodeJsonElement(),
            { RedactedEventContentSerializer(type) },
        ) {
            log.warn(it) { "could not deserialize event content of type $type" }
            UnknownEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        encoder.encodeJsonElement(
            canonicalJson(
                encoder.json.encodeToJsonElement(serializer as KSerializer<MessageEventContent>, value)
            )
        )
    }

    class NewContentToRelatesToSerializer(
        val type: String,
        baseSerializer: KSerializer<MessageEventContent>
    ) : JsonTransformingSerializer<MessageEventContent>(baseSerializer) {
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
    override val descriptor = buildClassSerialDescriptor("ContextualMessageEventContent")

    override fun deserialize(decoder: Decoder): MessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = (jsonObject["type"] as? JsonPrimitive)?.contentOrNull // this is a fallback (e.g. for RelatesTo)
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

internal class ContextualStateEventContentSerializer(
    private val mappings: Set<StateEventContentSerializerMapping>,
) : KSerializer<StateEventContent> {
    override val descriptor = buildClassSerialDescriptor("ContextualStateEventContent")

    override fun deserialize(decoder: Decoder): StateEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = (jsonObject["type"] as? JsonPrimitive)?.contentOrNull // this is a fallback (e.g. for unsigned)
            ?: throw SerializationException("type must not be null for deserializing StateEventContent")

        val serializer = mappings.contentSerializer(type)

        return decoder.json.decodeFromJsonElement(serializer, jsonObject)
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        require(encoder is JsonEncoder)
        val serializer = mappings.contentSerializer(value)
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}

class StateEventContentSerializer(
    private val type: String,
    private val baseSerializer: KSerializer<out StateEventContent>,
) : KSerializer<StateEventContent> {
    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer-$type")

    override fun deserialize(decoder: Decoder): StateEventContent {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            baseSerializer,
            decoder.decodeJsonElement(),
            { RedactedEventContentSerializer(type) },
        ) {
            log.warn(it) { "could not deserialize event content of type $type" }
            UnknownEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        encoder.encodeJsonElement(
            canonicalJson(
                encoder.json.encodeToJsonElement(baseSerializer as KSerializer<StateEventContent>, value)
            )
        )
    }
}
