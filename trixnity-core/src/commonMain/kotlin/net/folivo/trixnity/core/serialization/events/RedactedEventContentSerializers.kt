package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent


class RedactedMessageEventContentSerializer(val eventType: String) : KSerializer<RedactedMessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("RedactedMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): RedactedMessageEventContent {
        require(decoder is JsonDecoder)
        return RedactedMessageEventContent(eventType)
    }

    override fun serialize(encoder: Encoder, value: RedactedMessageEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(mapOf()))
    }
}

class RedactedStateEventContentSerializer(val eventType: String) : KSerializer<RedactedStateEventContent> {
    override val descriptor = buildClassSerialDescriptor("RedactedStateEventContentSerializer")

    override fun deserialize(decoder: Decoder): RedactedStateEventContent {
        require(decoder is JsonDecoder)
        return RedactedStateEventContent(eventType)
    }

    override fun serialize(encoder: Encoder, value: RedactedStateEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(mapOf()))
    }
}