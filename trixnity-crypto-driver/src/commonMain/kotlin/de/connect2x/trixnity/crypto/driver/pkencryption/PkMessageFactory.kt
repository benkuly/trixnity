package de.connect2x.trixnity.crypto.driver.pkencryption

interface PkMessageFactory {
    operator fun invoke(cipherText: String, mac: String, ephemeralKey: String): PkMessage
}