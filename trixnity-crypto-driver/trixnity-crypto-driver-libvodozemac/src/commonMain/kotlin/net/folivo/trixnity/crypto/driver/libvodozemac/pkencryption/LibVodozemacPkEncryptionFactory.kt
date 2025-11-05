package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkEncryptionFactory
import net.folivo.trixnity.vodozemac.Curve25519PublicKey
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryption

object LibVodozemacPkEncryptionFactory : PkEncryptionFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacPkEncryption
        = Curve25519PublicKey(bytes).use { LibVodozemacPkEncryption(PkEncryption(it)) }

    override fun invoke(base64: String): LibVodozemacPkEncryption
        = Curve25519PublicKey(base64).use { LibVodozemacPkEncryption(PkEncryption(it)) }
}