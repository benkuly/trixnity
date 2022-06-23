package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.EmptyEventContent
import net.folivo.trixnity.core.model.events.ClientEvent.UnknownEvent
import net.folivo.trixnity.core.serialization.canonicalJson

object UnknownEventSerializer : KSerializer<UnknownEvent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownEventSerializer")

    override fun deserialize(decoder: Decoder): UnknownEvent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")

        return UnknownEvent(EmptyEventContent, type, jsonObj)
    }

    override fun serialize(encoder: Encoder, value: UnknownEvent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(value.raw))
    }
}