package de.connect2x.trixnity.crypto.driver.pkencryption

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Curve25519SecretKey

interface PkDecryption : AutoCloseable {
    val secretKey: Curve25519SecretKey
    val publicKey: Curve25519PublicKey

    fun decrypt(message: PkMessage): String
}