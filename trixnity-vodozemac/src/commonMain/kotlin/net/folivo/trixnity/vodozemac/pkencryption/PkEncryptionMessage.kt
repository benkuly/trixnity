package net.folivo.trixnity.vodozemac.pkencryption

import net.folivo.trixnity.vodozemac.Curve25519PublicKey

sealed interface PkEncryptionMessage {
    val ciphertext: ByteArray
    val mac: ByteArray
    val ephemeralKey: Curve25519PublicKey

    class Text(
        override val ciphertext: ByteArray,
        override val mac: ByteArray,
        override val ephemeralKey: Curve25519PublicKey,
    ) : PkEncryptionMessage {
        companion object
    }

    class Bytes(
        override val ciphertext: ByteArray,
        override val mac: ByteArray,
        override val ephemeralKey: Curve25519PublicKey,
    ) : PkEncryptionMessage {
        companion object
    }
}
