package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class EphemeralEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out EphemeralEventContent>>
) : KSerializer<EphemeralEventContent> {
    override val descriptor = buildClassSerialDescriptor("EphemeralEventContentSerializer")

    override fun deserialize(decoder: Decoder): EphemeralEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: EphemeralEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<EphemeralEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}