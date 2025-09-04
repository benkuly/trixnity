package net.folivo.trixnity.crypto.driver.pkencryption

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey

interface PkMessage : AutoCloseable {
    val ciphertext: ByteArray
    val mac: ByteArray
    val ephemeralKey: Curve25519PublicKey
}