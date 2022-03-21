package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.RoomEventContent

class RoomEventContentSerializer(
    private val type: String,
    private val mappings: Set<EventContentSerializerMapping<out RoomEventContent>>
) : KSerializer<RoomEventContent> {
    override val descriptor = buildClassSerialDescriptor("RoomEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: RoomEventContent) {
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<RoomEventContent>
        encoder.encodeSerializableValue(serializer, value)
    }
}