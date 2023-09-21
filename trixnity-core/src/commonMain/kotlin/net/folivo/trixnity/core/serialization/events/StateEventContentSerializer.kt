package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger { }

class StateEventContentSerializer(
    private val mappings: Set<SerializerMapping<out StateEventContent>>,
    private val type: String? = null
) : KSerializer<StateEventContent> {
    override val descriptor = buildClassSerialDescriptor("StateEventContentSerializer")

    override fun deserialize(decoder: Decoder): StateEventContent {
        val type = this.type
            ?: throw SerializationException("type must not be null for deserializing StateEventContent")
        require(decoder is JsonDecoder)
        return decoder.json.tryDeserializeOrElse(
            mappings.contentDeserializer(type),
            decoder.decodeJsonElement(),
            lazy { RedactedStateEventContentSerializer(type) },
        ) {
            log.warn(it) { "could not deserialize content of type $type" }
            UnknownStateEventContentSerializer(type)
        }
    }

    override fun serialize(encoder: Encoder, value: StateEventContent) {
        require(encoder is JsonEncoder)
        @Suppress("UNCHECKED_CAST")
        val serializer = mappings.contentSerializer(value) as KSerializer<StateEventContent>
        encoder.encodeJsonElement(canonicalJson(encoder.json.encodeToJsonElement(serializer, value)))
    }
}