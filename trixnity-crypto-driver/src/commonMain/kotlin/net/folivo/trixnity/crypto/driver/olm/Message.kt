package net.folivo.trixnity.crypto.driver.olm

sealed interface Message : AutoCloseable {
    val bytes: ByteArray
    val base64: String

    interface Normal : Message
    interface PreKey : Message
}