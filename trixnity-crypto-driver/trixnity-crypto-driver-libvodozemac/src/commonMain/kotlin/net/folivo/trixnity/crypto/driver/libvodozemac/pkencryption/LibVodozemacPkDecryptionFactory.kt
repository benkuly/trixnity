package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryptionFactory
import net.folivo.trixnity.vodozemac.Curve25519SecretKey
import net.folivo.trixnity.vodozemac.pkencryption.PkDecryption

object LibVodozemacPkDecryptionFactory : PkDecryptionFactory {
    override fun invoke(): LibVodozemacPkDecryption
        = LibVodozemacPkDecryption(PkDecryption())

    override fun invoke(bytes: ByteArray): LibVodozemacPkDecryption
        = Curve25519SecretKey(bytes).use { LibVodozemacPkDecryption(PkDecryption(it)) }

    override fun invoke(base64: String): LibVodozemacPkDecryption
        = Curve25519SecretKey(base64).use { LibVodozemacPkDecryption(PkDecryption(it)) }
}