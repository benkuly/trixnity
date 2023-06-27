package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

class RoomAccountDataEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out RoomAccountDataEventContent>>
) : KSerializer<RoomAccountDataEventContent> {
    override val descriptor = buildClassSerialDescriptor("RoomAccountDataEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomAccountDataEventContent {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            mappings.contentDeserializer(type), decoder.decodeJsonElement()
        ) {
            log.warn(it) { "could not deserialize content of type $type" }
            UnknownRoomAccountDataEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: RoomAccountDataEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<RoomAccountDataEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}