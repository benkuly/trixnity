package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class StateEventContentSerializer(
    private val mappings: Set<SerializerMapping<out StateEventContent>>,
    private val type: String? = null
) : KSerializer<StateEventContent> {
    companion object {
        fun withRedaction(
            mappings: Set<SerializerMapping<out StateEventContent>>,
            type: String,
            isRedacted: Boolean,
        ): KSerializer<out StateEventContent> =
            if (isRedacted) RedactedStateEventContentSerializer(type)
            else StateEventContentSerializer(mappings, type)
    }

    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer")

    override fun deserialize(decoder: Decoder): StateEventContent {
        val type = this.type
            ?: throw SerializationException("type must not be null for deserializing StateEventContent")
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value) as KSerializer<StateEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}