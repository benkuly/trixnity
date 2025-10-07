package net.folivo.trixnity.crypto.driver.megolm

interface SessionKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}

