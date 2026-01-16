package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkFactory

object LibOlmPkFactory : PkFactory {
    override val encryption: LibOlmPkEncryptionFactory = LibOlmPkEncryptionFactory
    override val decryption: LibOlmPkDecryptionFactory = LibOlmPkDecryptionFactory
    override val message: LibOlmPkMessageFactory = LibOlmPkMessageFactory
}