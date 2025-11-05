package net.folivo.trixnity.crypto.driver.megolm

interface ExportedSessionKeyFactory {
    operator fun invoke(bytes: ByteArray): ExportedSessionKey
    operator fun invoke(base64: String): ExportedSessionKey
}