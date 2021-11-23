package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

object KeyAlgorithmSerializer : KSerializer<KeyAlgorithm> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm {
        return KeyAlgorithm.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("KeyAlgorithmSerializer", PrimitiveKind.STRING)
}

object Ed25519KeyAlgorithmSerializer : KSerializer<KeyAlgorithm.Ed25519> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm.Ed25519 {
        return KeyAlgorithm.Ed25519
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm.Ed25519) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Ed25519KeyAlgorithmSerializer", PrimitiveKind.STRING)
}

object Curve25519KeyAlgorithmSerializer : KSerializer<KeyAlgorithm.Curve25519> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm.Curve25519 {
        return KeyAlgorithm.Curve25519
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm.Curve25519) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Curve25519KeyAlgorithmSerializer", PrimitiveKind.STRING)
}

object SignedCurve25519KeyAlgorithmSerializer : KSerializer<KeyAlgorithm.SignedCurve25519> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm.SignedCurve25519 {
        return KeyAlgorithm.SignedCurve25519
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm.SignedCurve25519) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SignedCurve25519KeyAlgorithmSerializer", PrimitiveKind.STRING)
}

object UnknownKeyAlgorithmSerializer : KSerializer<KeyAlgorithm.Unknown> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm.Unknown {
        return KeyAlgorithm.Unknown(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm.Unknown) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UnknownKeyAlgorithmSerializer", PrimitiveKind.STRING)
}