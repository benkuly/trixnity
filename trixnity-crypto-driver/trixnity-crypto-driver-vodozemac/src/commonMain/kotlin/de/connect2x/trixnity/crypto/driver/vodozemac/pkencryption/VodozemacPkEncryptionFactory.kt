package de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkEncryptionFactory
import de.connect2x.trixnity.vodozemac.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.pkencryption.PkEncryption

object VodozemacPkEncryptionFactory : PkEncryptionFactory {
    override fun invoke(bytes: ByteArray): VodozemacPkEncryption =
        Curve25519PublicKey(bytes).use { VodozemacPkEncryption(PkEncryption(it)) }

    override fun invoke(base64: String): VodozemacPkEncryption =
        Curve25519PublicKey(base64).use { VodozemacPkEncryption(PkEncryption(it)) }
}