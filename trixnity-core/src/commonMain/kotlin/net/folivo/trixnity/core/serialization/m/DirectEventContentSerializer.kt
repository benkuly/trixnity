package net.folivo.trixnity.core.serialization.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.serialization.RoomIdSerializer
import net.folivo.trixnity.core.serialization.UserIdSerializer

object DirectEventContentSerializer : KSerializer<DirectEventContent> {
    override val descriptor = buildClassSerialDescriptor("DirectEventContentSerializer")

    override fun deserialize(decoder: Decoder): DirectEventContent {
        require(decoder is JsonDecoder)
        val serializer = MapSerializer(UserIdSerializer, SetSerializer(RoomIdSerializer).nullable)
        return DirectEventContent(decoder.json.decodeFromJsonElement(serializer, decoder.decodeJsonElement()))
    }

    override fun serialize(encoder: Encoder, value: DirectEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value.mappings))
    }
}