package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.Ed25519SecretKeyFactory
import de.connect2x.trixnity.vodozemac.Ed25519SecretKey

object VodozemacEd25519SecretKeyFactory : Ed25519SecretKeyFactory {
    override fun invoke(): VodozemacEd25519SecretKey = VodozemacEd25519SecretKey(Ed25519SecretKey())

    override fun invoke(bytes: ByteArray): VodozemacEd25519SecretKey =
        VodozemacEd25519SecretKey(Ed25519SecretKey(bytes))

    override fun invoke(base64: String): VodozemacEd25519SecretKey =
        VodozemacEd25519SecretKey(Ed25519SecretKey(base64))
}