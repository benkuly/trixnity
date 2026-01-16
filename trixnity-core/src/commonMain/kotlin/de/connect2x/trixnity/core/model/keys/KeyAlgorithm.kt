package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.serialization.stringWrapperSerializer

@Serializable(with = KeyAlgorithm.Serializer::class)
sealed class KeyAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = Ed25519.Serializer::class)
    data object Ed25519 : KeyAlgorithm() {
        override val name = "ed25519"

        object Serializer : KSerializer<Ed25519> by stringWrapperSerializer(Ed25519, name)
    }

    @Serializable(with = Curve25519.Serializer::class)
    data object Curve25519 : KeyAlgorithm() {
        override val name = "curve25519"

        object Serializer : KSerializer<Curve25519> by stringWrapperSerializer(Curve25519, name)
    }

    @Serializable(with = SignedCurve25519.Serializer::class)
    data object SignedCurve25519 : KeyAlgorithm() {
        override val name = "signed_curve25519"

        object Serializer : KSerializer<SignedCurve25519> by stringWrapperSerializer(SignedCurve25519, name)
    }

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

    object Serializer : KSerializer<KeyAlgorithm> {
        override fun deserialize(decoder: Decoder): KeyAlgorithm {
            return of(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: KeyAlgorithm) {
            encoder.encodeString(value.name)
        }

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("KeyAlgorithm", PrimitiveKind.STRING)
    }
}