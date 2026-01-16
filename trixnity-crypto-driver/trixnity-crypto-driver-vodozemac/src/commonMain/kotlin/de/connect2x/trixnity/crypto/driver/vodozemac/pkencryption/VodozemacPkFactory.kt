package de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkFactory

object VodozemacPkFactory : PkFactory {
    override val encryption: VodozemacPkEncryptionFactory = VodozemacPkEncryptionFactory
    override val decryption: VodozemacPkDecryptionFactory = VodozemacPkDecryptionFactory
    override val message: VodozemacPkMessageFactory = VodozemacPkMessageFactory
}