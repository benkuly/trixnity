package de.connect2x.trixnity.crypto.driver.sas

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey

interface Sas : AutoCloseable {
    val publicKey: Curve25519PublicKey

    fun diffieHellman(theirPublicKey: Curve25519PublicKey): EstablishedSas
}