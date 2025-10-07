package net.folivo.trixnity.crypto.driver.keys

interface Curve25519SecretKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String

    val publicKey: Curve25519PublicKey
}