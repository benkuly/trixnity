package net.folivo.trixnity.crypto.driver.pkencryption

interface PkEncryption : AutoCloseable {
    fun encrypt(plaintext: String): PkMessage
}