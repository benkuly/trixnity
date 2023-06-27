package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

class ToDeviceEventContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out ToDeviceEventContent>>
) : KSerializer<ToDeviceEventContent> {
    override val descriptor = buildClassSerialDescriptor("ToDeviceEventContentSerializer")

    override fun deserialize(decoder: Decoder): ToDeviceEventContent {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            mappings.contentDeserializer(type), decoder.decodeJsonElement()
        ) {
            log.warn(it) { "could not deserialize content of type $type" }
            UnknownToDeviceEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: ToDeviceEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<ToDeviceEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}