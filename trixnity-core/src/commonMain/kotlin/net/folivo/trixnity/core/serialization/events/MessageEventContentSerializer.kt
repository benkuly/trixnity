package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.MessageEventContent

class MessageEventContentSerializer(
    private val type: String,
    private val isRedacted: Boolean,
    private val mappings: Set<EventContentSerializerMapping<out MessageEventContent>>
) : KSerializer<MessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("MessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): MessageEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type, isRedacted))
    }

    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<MessageEventContent>
        encoder.encodeSerializableValue(serializer, value)
    }
}