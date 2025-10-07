package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkFactory

object LibOlmPkFactory : PkFactory {
    override val encryption: LibOlmPkEncryptionFactory = LibOlmPkEncryptionFactory
    override val decryption: LibOlmPkDecryptionFactory = LibOlmPkDecryptionFactory
    override val message: LibOlmPkMessageFactory = LibOlmPkMessageFactory
}