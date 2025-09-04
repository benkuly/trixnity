package net.folivo.trixnity.crypto.driver.megolm

interface MegolmMessageFactory {
    operator fun invoke(bytes: ByteArray): MegolmMessage
    operator fun invoke(base64: String): MegolmMessage
}