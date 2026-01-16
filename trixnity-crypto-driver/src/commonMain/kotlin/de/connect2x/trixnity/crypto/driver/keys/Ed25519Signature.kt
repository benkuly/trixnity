package de.connect2x.trixnity.crypto.driver.keys

interface Ed25519Signature : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}