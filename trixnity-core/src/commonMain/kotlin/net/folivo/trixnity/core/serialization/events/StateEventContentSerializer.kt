package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.StateEventContent

class StateEventContentSerializer(
    private val type: String,
    private val isRedacted: Boolean,
    private val mappings: Set<EventContentSerializerMapping<out StateEventContent>>
) : KSerializer<StateEventContent> {
    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer")

    override fun deserialize(decoder: Decoder): StateEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type, isRedacted))
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<StateEventContent>
        encoder.encodeSerializableValue(serializer, value)
    }
}