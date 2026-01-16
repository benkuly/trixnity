package de.connect2x.trixnity.crypto.driver.olm

interface PreKeyMessageFactory {
    operator fun invoke(bytes: ByteArray): Message.PreKey
    operator fun invoke(base64: String): Message.PreKey
}