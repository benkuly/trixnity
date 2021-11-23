package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType

object OlmMessageTypeSerializer : KSerializer<OlmMessageType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OlmMessageTypeSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): OlmMessageType {
        return OlmMessageType.of(decoder.decodeInt())
    }

    override fun serialize(encoder: Encoder, value: OlmMessageType) {
        encoder.encodeInt(value.value)
    }

}