package de.connect2x.trixnity.crypto.driver.megolm

interface SessionKeyFactory {
    operator fun invoke(bytes: ByteArray): SessionKey
    operator fun invoke(base64: String): SessionKey
}