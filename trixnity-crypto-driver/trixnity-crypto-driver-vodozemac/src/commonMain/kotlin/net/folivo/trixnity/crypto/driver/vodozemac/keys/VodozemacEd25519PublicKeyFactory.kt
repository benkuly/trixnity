package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKeyFactory
import net.folivo.trixnity.vodozemac.Ed25519PublicKey

object VodozemacEd25519PublicKeyFactory : Ed25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacEd25519PublicKey =
        VodozemacEd25519PublicKey(Ed25519PublicKey(bytes))

    override fun invoke(base64: String): VodozemacEd25519PublicKey =
        VodozemacEd25519PublicKey(Ed25519PublicKey(base64))
}