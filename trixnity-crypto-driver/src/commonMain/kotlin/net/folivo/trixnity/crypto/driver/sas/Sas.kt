package net.folivo.trixnity.crypto.driver.sas

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey

interface Sas : AutoCloseable {
    val publicKey: Curve25519PublicKey

    fun diffieHellman(theirPublicKey: Curve25519PublicKey): EstablishedSas
}