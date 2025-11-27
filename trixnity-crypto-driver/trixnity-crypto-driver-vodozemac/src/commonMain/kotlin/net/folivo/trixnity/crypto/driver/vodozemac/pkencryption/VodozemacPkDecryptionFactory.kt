package net.folivo.trixnity.crypto.driver.vodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryptionFactory
import net.folivo.trixnity.vodozemac.Curve25519SecretKey
import net.folivo.trixnity.vodozemac.pkencryption.PkDecryption

object VodozemacPkDecryptionFactory : PkDecryptionFactory {
    override fun invoke(): VodozemacPkDecryption = VodozemacPkDecryption(PkDecryption())

    override fun invoke(bytes: ByteArray): VodozemacPkDecryption =
        Curve25519SecretKey(bytes).use { VodozemacPkDecryption(PkDecryption(it)) }

    override fun invoke(base64: String): VodozemacPkDecryption =
        Curve25519SecretKey(base64).use { VodozemacPkDecryption(PkDecryption(it)) }
}