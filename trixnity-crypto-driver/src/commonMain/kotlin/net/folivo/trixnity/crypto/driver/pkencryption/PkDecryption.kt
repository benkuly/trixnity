package net.folivo.trixnity.crypto.driver.pkencryption

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKey

interface PkDecryption : AutoCloseable {
    val secretKey: Curve25519SecretKey
    val publicKey: Curve25519PublicKey

    fun decrypt(message: PkMessage): String
}