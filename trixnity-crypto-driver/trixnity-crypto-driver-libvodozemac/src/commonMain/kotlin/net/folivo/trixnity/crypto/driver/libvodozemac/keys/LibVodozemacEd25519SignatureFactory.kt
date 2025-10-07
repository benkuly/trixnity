package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SignatureFactory
import net.folivo.trixnity.vodozemac.Ed25519Signature

object LibVodozemacEd25519SignatureFactory : Ed25519SignatureFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacEd25519Signature
        = LibVodozemacEd25519Signature(Ed25519Signature(bytes))

    override fun invoke(base64: String): LibVodozemacEd25519Signature
        = LibVodozemacEd25519Signature(Ed25519Signature(base64))
}