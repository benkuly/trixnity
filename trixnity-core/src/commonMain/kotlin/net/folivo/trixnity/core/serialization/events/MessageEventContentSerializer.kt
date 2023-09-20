package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

// TODO hopefully a new spec removes the m.new_content (m.replace) hack
class MessageEventContentSerializer(
    private val mappings: Set<SerializerMapping<out MessageEventContent>>,
    private val type: String? = null,
) : KSerializer<MessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("MessageEventContentSerializer")
    override fun deserialize(decoder: Decoder): MessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = this.type
            ?: jsonObject["type"]?.jsonPrimitive?.content // this is a fallback (e.g. for RelatesTo)
            ?: throw SerializationException("type must not be null for deserializing MessageEventContent")

        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentDeserializer(type) as KSerializer<MessageEventContent>
        return decoder.json.tryDeserializeOrElse(
            NewContentToRelatesToSerializer(type, serializer),
            jsonObject,
            lazy { RedactedMessageEventContentSerializer(type) },
        ) {
            log.warn(it) { "could not deserialize content of type $type" }
            UnknownMessageEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value) as KSerializer<MessageEventContent>
        val type = mappings.contentType(value)
        encoder.encodeJsonElement(
            canonicalJson(
                encoder.json.encodeToJsonElement(NewContentToRelatesToSerializer(type, serializer), value)
            )
        )
    }

    class NewContentToRelatesToSerializer(
        val type: String,
        baseSerializer: KSerializer<MessageEventContent>
    ) :
        JsonTransformingSerializer<MessageEventContent>(baseSerializer) {
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

