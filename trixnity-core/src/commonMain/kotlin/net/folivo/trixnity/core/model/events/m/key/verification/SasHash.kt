package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SasHash.Serializer::class)
sealed interface SasHash {
    val name: String

    data object Sha256 : SasHash {
        override val name: String = "sha256"
    }

    data class Unknown(override val name: String) : SasHash

    class Serializer : KSerializer<SasHash> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("SasHash", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): SasHash =
            when (val name = decoder.decodeString()) {
                Sha256.name -> Sha256
                else -> Unknown(name)
            }

        override fun serialize(encoder: Encoder, value: SasHash) =
            encoder.encodeString(value.name)
    }
}