package net.folivo.trixnity.crypto.driver.pkencryption

interface PkEncryptionFactory {
    operator fun invoke(bytes: ByteArray): PkEncryption
    operator fun invoke(base64: String): PkEncryption
}