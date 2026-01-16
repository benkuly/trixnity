package de.connect2x.trixnity.crypto.driver.pkencryption

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey

interface PkMessage : AutoCloseable {
    val ciphertext: ByteArray
    val mac: ByteArray
    val ephemeralKey: Curve25519PublicKey
}