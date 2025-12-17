package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SignatureFactory
import net.folivo.trixnity.vodozemac.Ed25519Signature

object VodozemacEd25519SignatureFactory : Ed25519SignatureFactory {
    override fun invoke(bytes: ByteArray): VodozemacEd25519Signature =
        VodozemacEd25519Signature(Ed25519Signature(bytes))

    override fun invoke(base64: String): VodozemacEd25519Signature =
        VodozemacEd25519Signature(Ed25519Signature(base64))
}