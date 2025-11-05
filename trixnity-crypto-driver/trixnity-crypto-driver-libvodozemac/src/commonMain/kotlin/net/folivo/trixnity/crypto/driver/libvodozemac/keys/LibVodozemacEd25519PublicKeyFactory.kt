package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKeyFactory
import net.folivo.trixnity.vodozemac.Ed25519PublicKey

object LibVodozemacEd25519PublicKeyFactory : Ed25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacEd25519PublicKey
        = LibVodozemacEd25519PublicKey(Ed25519PublicKey(bytes))

    override fun invoke(base64: String): LibVodozemacEd25519PublicKey
        = LibVodozemacEd25519PublicKey(Ed25519PublicKey(base64))
}