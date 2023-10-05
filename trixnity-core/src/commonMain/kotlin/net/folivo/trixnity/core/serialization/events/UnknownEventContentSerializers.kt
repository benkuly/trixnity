package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.serialization.canonicalJson

class UnknownEventContentSerializer(val eventType: String) : KSerializer<UnknownEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownEventContentSerializer")
    override fun deserialize(decoder: Decoder): UnknownEventContent {
        require(decoder is JsonDecoder)
        return UnknownEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(value.raw))
    }
}