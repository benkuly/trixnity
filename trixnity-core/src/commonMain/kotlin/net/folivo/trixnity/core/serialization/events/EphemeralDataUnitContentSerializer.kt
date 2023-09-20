package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

class EphemeralDataUnitContentSerializer(
    private val type: String,
    private val mappings: Set<SerializerMapping<out EphemeralDataUnitContent>>
) : KSerializer<EphemeralDataUnitContent> {
    override val descriptor = buildClassSerialDescriptor("EphemeralDataUnitContentSerializer")

    override fun deserialize(decoder: Decoder): EphemeralDataUnitContent {
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            mappings.contentDeserializer(type), decoder.decodeJsonElement()
        ) {
            log.warn(it) { "could not deserialize ephemeral data unit content of type $type" }
            UnknownEphemeralDataUnitContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: EphemeralDataUnitContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value).second as KSerializer<EphemeralDataUnitContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}