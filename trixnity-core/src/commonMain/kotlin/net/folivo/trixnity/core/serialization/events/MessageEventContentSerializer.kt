package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class MessageEventContentSerializer(
    private val mappings: Set<SerializerMapping<out MessageEventContent>>,
    private val type: String? = null,
) : KSerializer<MessageEventContent> {
    companion object {
        fun withRedaction(
            mappings: Set<SerializerMapping<out MessageEventContent>>,
            type: String,
            isRedacted: Boolean,
        ): KSerializer<out MessageEventContent> =
            if (isRedacted) RedactedMessageEventContentSerializer(type)
            else MessageEventContentSerializer(mappings, type)
    }

    override val descriptor = buildClassSerialDescriptor("MessageEventContentSerializer")
    override fun deserialize(decoder: Decoder): MessageEventContent {
        val type = this.type
            ?: throw SerializationException("type must not be null for deserializing MessageEventContent")
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value) as KSerializer<MessageEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}

