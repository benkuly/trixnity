package de.connect2x.trixnity.crypto.driver.megolm

interface ExportedSessionKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}


