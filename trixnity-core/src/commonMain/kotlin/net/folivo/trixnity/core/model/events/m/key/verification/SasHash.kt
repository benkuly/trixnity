package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SasHashSerializer::class)
interface SasHash {
    val name: String

    object Sha256 : SasHash {
        override val name: String = "sha256"
    }

    data class Unknown(override val name: String) : SasHash
}

class SasHashSerializer : KSerializer<SasHash> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SasHash", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SasHash =
        when (val name = decoder.decodeString()) {
            SasHash.Sha256.name -> SasHash.Sha256
            else -> SasHash.Unknown(name)
        }

    override fun serialize(encoder: Encoder, value: SasHash) =
        encoder.encodeString(value.name)
}