package de.connect2x.trixnity.crypto.driver.pkencryption

interface PkFactory {
    val encryption: PkEncryptionFactory
    val decryption: PkDecryptionFactory
    val message: PkMessageFactory
}