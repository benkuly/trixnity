package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class RoomAccountDataEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out RoomAccountDataEventContent>>
) : KSerializer<RoomAccountDataEventContent> {
    override val descriptor = buildClassSerialDescriptor("RoomAccountDataEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomAccountDataEventContent {
        return decoder.decodeSerializableValue(mappings.contentDeserializer(type))
    }

    override fun serialize(encoder: Encoder, value: RoomAccountDataEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<RoomAccountDataEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}