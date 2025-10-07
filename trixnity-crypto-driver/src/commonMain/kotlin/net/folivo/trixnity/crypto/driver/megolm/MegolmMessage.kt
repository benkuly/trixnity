package net.folivo.trixnity.crypto.driver.megolm

interface MegolmMessage : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}

