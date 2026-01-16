package de.connect2x.trixnity.crypto.driver.pkencryption

interface PkEncryption : AutoCloseable {
    fun encrypt(plaintext: String): PkMessage
}