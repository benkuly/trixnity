package net.folivo.trixnity.crypto.driver.megolm

interface ExportedSessionKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}


