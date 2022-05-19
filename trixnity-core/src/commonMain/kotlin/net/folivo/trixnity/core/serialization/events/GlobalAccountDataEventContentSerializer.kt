package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

class GlobalAccountDataEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out GlobalAccountDataEventContent>>
) : KSerializer<GlobalAccountDataEventContent> {
    override val descriptor = buildClassSerialDescriptor("GlobalAccountDataEventContentSerializer")

    override fun deserialize(decoder: Decoder): GlobalAccountDataEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: GlobalAccountDataEventContent) {
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<GlobalAccountDataEventContent>
        encoder.encodeSerializableValue(serializer, value)
    }
}