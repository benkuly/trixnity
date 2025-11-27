package net.folivo.trixnity.crypto.driver.vodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkFactory

object VodozemacPkFactory : PkFactory {
    override val encryption: VodozemacPkEncryptionFactory = VodozemacPkEncryptionFactory
    override val decryption: VodozemacPkDecryptionFactory = VodozemacPkDecryptionFactory
    override val message: VodozemacPkMessageFactory = VodozemacPkMessageFactory
}