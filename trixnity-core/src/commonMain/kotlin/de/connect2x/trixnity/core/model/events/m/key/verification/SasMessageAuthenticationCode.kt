package de.connect2x.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SasMessageAuthenticationCode.Serializer::class)
sealed interface SasMessageAuthenticationCode {
    val name: String

    data object HkdfHmacSha256 : SasMessageAuthenticationCode {
        override val name: String = "hkdf-hmac-sha256"
    }

    data object HkdfHmacSha256V2 : SasMessageAuthenticationCode {
        override val name: String = "hkdf-hmac-sha256.v2"
    }

    data class Unknown(override val name: String) : SasMessageAuthenticationCode

    class Serializer : KSerializer<SasMessageAuthenticationCode> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("SasMessageAuthenticationCode", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): SasMessageAuthenticationCode =
            when (val name = decoder.decodeString()) {
                HkdfHmacSha256.name -> HkdfHmacSha256
                HkdfHmacSha256V2.name -> HkdfHmacSha256V2
                else -> Unknown(name)
            }

        override fun serialize(encoder: Encoder, value: SasMessageAuthenticationCode) =
            encoder.encodeString(value.name)
    }
}