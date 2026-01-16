package de.connect2x.trixnity.crypto.driver.pkencryption

interface PkDecryptionFactory {
    operator fun invoke(): PkDecryption
    operator fun invoke(bytes: ByteArray): PkDecryption
    operator fun invoke(base64: String): PkDecryption
}