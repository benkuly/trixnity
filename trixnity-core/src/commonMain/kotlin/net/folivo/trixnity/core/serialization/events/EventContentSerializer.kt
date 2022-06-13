package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class EventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out EventContent>>
) : KSerializer<EventContent> {
    override val descriptor = buildClassSerialDescriptor("EventContentSerializer")

    override fun deserialize(decoder: Decoder): EventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: EventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<EventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}