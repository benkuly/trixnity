package net.folivo.trixnity.crypto.driver.olm

interface NormalMessageFactory {
    operator fun invoke(bytes: ByteArray): Message.Normal
    operator fun invoke(base64: String): Message.Normal
}