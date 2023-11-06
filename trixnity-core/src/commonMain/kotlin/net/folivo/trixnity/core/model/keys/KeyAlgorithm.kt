package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = KeyAlgorithmSerializer::class)
sealed class KeyAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = Ed25519KeyAlgorithmSerializer::class)
    data object Ed25519 : KeyAlgorithm() {
        override val name = "ed25519"
    }

    @Serializable(with = Curve25519KeyAlgorithmSerializer::class)
    data object Curve25519 : KeyAlgorithm() {
        override val name = "curve25519"
    }

    @Serializable(with = SignedCurve25519KeyAlgorithmSerializer::class)
    data object SignedCurve25519 : KeyAlgorithm() {
        override val name = "signed_curve25519"
    }

    @Serializable(with = UnknownKeyAlgorithmSerializer::class)
    data class Unknown(override val name: String) : KeyAlgorithm()

    companion object {
        fun of(name: String): KeyAlgorithm {
            if (name.isEmpty()) throw IllegalArgumentException("key algorithm must not be empty")
            return when (name) {
                Ed25519.name -> Ed25519
                Curve25519.name -> Curve25519
                SignedCurve25519.name -> SignedCurve25519
                else -> Unknown(name)
            }
        }
    }
}

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