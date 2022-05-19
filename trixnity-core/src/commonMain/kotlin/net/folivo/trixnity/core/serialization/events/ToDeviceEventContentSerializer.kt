package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

class ToDeviceEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out ToDeviceEventContent>>
) : KSerializer<ToDeviceEventContent> {
    override val descriptor = buildClassSerialDescriptor("ToDeviceEventContentSerializer")

    override fun deserialize(decoder: Decoder): ToDeviceEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: ToDeviceEventContent) {
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<ToDeviceEventContent>
        encoder.encodeSerializableValue(serializer, value)
    }
}