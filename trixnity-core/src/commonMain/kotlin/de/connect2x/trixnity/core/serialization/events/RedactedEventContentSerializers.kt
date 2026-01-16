package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.events.RedactedEventContent


class RedactedEventContentSerializer(val eventType: String) : KSerializer<RedactedEventContent> {
    override val descriptor = buildClassSerialDescriptor("RedactedEventContent")

    override fun deserialize(decoder: Decoder): RedactedEventContent {
        require(decoder is JsonDecoder)
        return RedactedEventContent(eventType)
    }

    override fun serialize(encoder: Encoder, value: RedactedEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(mapOf()))
    }
}