package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.EncryptionAlgorithmSerializer

@Serializable(with = EncryptionAlgorithmSerializer::class)
sealed class EncryptionAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = EncryptionAlgorithmSerializer::class)
    object Megolm : EncryptionAlgorithm() {
        override val name: String
            get() = "m.megolm.v1.aes-sha2"
    }

    @Serializable(with = EncryptionAlgorithmSerializer::class)
    object Olm : EncryptionAlgorithm() {
        override val name: String
            get() = "m.olm.v1.curve25519-aes-sha2"
    }

    @Serializable(with = EncryptionAlgorithmSerializer::class)
    data class Unknown(override val name: String) : EncryptionAlgorithm()

    companion object {
        fun of(name: String): EncryptionAlgorithm {
            if (name.isEmpty()) throw IllegalArgumentException("encryption algorithm must not be empty")
            return when (name) {
                Megolm.name -> Megolm
                Olm.name -> Olm
                else -> Unknown(name)
            }
        }
    }
}