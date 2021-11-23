package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm

object EncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm {
        return EncryptionAlgorithm.of(decoder.decodeString())
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