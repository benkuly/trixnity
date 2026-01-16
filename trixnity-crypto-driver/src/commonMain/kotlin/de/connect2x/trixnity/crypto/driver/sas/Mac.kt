package de.connect2x.trixnity.crypto.driver.sas

interface Mac : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}