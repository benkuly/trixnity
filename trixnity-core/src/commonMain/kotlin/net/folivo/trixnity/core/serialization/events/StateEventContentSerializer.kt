package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class StateEventContentSerializer(
    private val type: String,
    private val isRedacted: Boolean,
    private val mappings: Set<SerializerMapping<out StateEventContent>>
) : KSerializer<StateEventContent> {
    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer")

    override fun deserialize(decoder: Decoder): StateEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type, isRedacted))
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<StateEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}