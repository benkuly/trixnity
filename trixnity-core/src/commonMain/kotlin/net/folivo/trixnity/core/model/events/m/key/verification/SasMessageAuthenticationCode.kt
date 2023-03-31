package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MessageAuthenticationCodeSerializer::class)
interface SasMessageAuthenticationCode {
    val name: String

    object HkdfHmacSha256 : SasMessageAuthenticationCode {
        override val name: String = "hkdf-hmac-sha256"
    }

    object HkdfHmacSha256V2 : SasMessageAuthenticationCode {
        override val name: String = "hkdf-hmac-sha256.v2"
    }

    data class Unknown(override val name: String) : SasMessageAuthenticationCode
}

class MessageAuthenticationCodeSerializer : KSerializer<SasMessageAuthenticationCode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SasMessageAuthenticationCode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SasMessageAuthenticationCode =
        when (val name = decoder.decodeString()) {
            SasMessageAuthenticationCode.HkdfHmacSha256.name -> SasMessageAuthenticationCode.HkdfHmacSha256
            SasMessageAuthenticationCode.HkdfHmacSha256V2.name -> SasMessageAuthenticationCode.HkdfHmacSha256V2
            else -> SasMessageAuthenticationCode.Unknown(name)
        }

    override fun serialize(encoder: Encoder, value: SasMessageAuthenticationCode) =
        encoder.encodeString(value.name)
}