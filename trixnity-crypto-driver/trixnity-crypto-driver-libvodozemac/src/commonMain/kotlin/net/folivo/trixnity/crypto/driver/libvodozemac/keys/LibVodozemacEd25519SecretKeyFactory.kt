package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKeyFactory
import net.folivo.trixnity.vodozemac.Ed25519SecretKey

object LibVodozemacEd25519SecretKeyFactory : Ed25519SecretKeyFactory {
    override fun invoke(): LibVodozemacEd25519SecretKey
        = LibVodozemacEd25519SecretKey(Ed25519SecretKey())

    override fun invoke(bytes: ByteArray): LibVodozemacEd25519SecretKey
        = LibVodozemacEd25519SecretKey(Ed25519SecretKey(bytes))

    override fun invoke(base64: String): LibVodozemacEd25519SecretKey
        = LibVodozemacEd25519SecretKey(Ed25519SecretKey(base64))
}