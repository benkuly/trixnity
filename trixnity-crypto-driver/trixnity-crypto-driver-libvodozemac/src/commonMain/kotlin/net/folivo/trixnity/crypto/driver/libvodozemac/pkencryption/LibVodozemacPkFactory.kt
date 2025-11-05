package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkFactory

object LibVodozemacPkFactory : PkFactory {
    override val encryption: LibVodozemacPkEncryptionFactory = LibVodozemacPkEncryptionFactory
    override val decryption: LibVodozemacPkDecryptionFactory = LibVodozemacPkDecryptionFactory
    override val message: LibVodozemacPkMessageFactory = LibVodozemacPkMessageFactory
}