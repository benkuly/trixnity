package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SasKeyAgreementProtocolSerializer::class)
sealed interface SasKeyAgreementProtocol {
    val name: String

    object Curve25519HkdfSha256 : SasKeyAgreementProtocol {
        override val name: String = "curve25519-hkdf-sha256"
    }

    data class Unknown(override val name: String) : SasKeyAgreementProtocol
}

class SasKeyAgreementProtocolSerializer : KSerializer<SasKeyAgreementProtocol> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SasKeyAgreementProtocol", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SasKeyAgreementProtocol =
        when (val name = decoder.decodeString()) {
            SasKeyAgreementProtocol.Curve25519HkdfSha256.name -> SasKeyAgreementProtocol.Curve25519HkdfSha256
            else -> SasKeyAgreementProtocol.Unknown(name)
        }

    override fun serialize(encoder: Encoder, value: SasKeyAgreementProtocol) =
        encoder.encodeString(value.name)
}