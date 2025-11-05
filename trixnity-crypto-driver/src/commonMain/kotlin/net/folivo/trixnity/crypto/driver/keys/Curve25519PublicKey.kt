package net.folivo.trixnity.crypto.driver.keys

interface Curve25519PublicKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String
}