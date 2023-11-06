package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = EncryptionAlgorithmSerializer::class)
sealed class EncryptionAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = MegolmEncryptionAlgorithmSerializer::class)
    data object Megolm : EncryptionAlgorithm() {
        override val name: String
            get() = "m.megolm.v1.aes-sha2"
    }

    @Serializable(with = OlmEncryptionAlgorithmSerializer::class)
    data object Olm : EncryptionAlgorithm() {
        override val name: String
            get() = "m.olm.v1.curve25519-aes-sha2"
    }

    @Serializable(with = UnknownEncryptionAlgorithmSerializer::class)
    data class Unknown(override val name: String) : EncryptionAlgorithm()
}

object EncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm {
        return when (val name = decoder.decodeString()) {
            EncryptionAlgorithm.Megolm.name -> EncryptionAlgorithm.Megolm
            EncryptionAlgorithm.Olm.name -> EncryptionAlgorithm.Olm
            else -> EncryptionAlgorithm.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptionAlgorithm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EncryptionAlgorithmSerializer", PrimitiveKind.STRING)
}

object MegolmEncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm.Megolm> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm.Megolm {
        return EncryptionAlgorithm.Megolm
    }

    override fun serialize(encoder: Encoder, value: EncryptionAlgorithm.Megolm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MegolmEncryptionAlgorithmSerializer", PrimitiveKind.STRING)
}

object OlmEncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm.Olm> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm.Olm {
        return EncryptionAlgorithm.Olm
    }

    override fun serialize(encoder: Encoder, value: EncryptionAlgorithm.Olm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OlmEncryptionAlgorithmSerializer", PrimitiveKind.STRING)
}

object UnknownEncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm.Unknown> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm.Unknown {
        return EncryptionAlgorithm.Unknown(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: EncryptionAlgorithm.Unknown) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UnknownEncryptionAlgorithmSerializer", PrimitiveKind.STRING)
}