package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.KeyAlgorithmSerializer

@Serializable(with = KeyAlgorithmSerializer::class)
sealed class KeyAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = KeyAlgorithmSerializer::class)
    object Ed25519 : KeyAlgorithm() {
        override val name = "ed25519"
    }

    @Serializable(with = KeyAlgorithmSerializer::class)
    object Curve25519 : KeyAlgorithm() {
        override val name = "curve25519"
    }

    @Serializable(with = KeyAlgorithmSerializer::class)
    object SignedCurve25519 : KeyAlgorithm() {
        override val name = "signed_curve25519"
    }

    @Serializable(with = KeyAlgorithmSerializer::class)
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