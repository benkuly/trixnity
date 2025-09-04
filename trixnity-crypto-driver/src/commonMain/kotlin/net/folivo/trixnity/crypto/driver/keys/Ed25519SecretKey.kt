package net.folivo.trixnity.crypto.driver.keys

interface Ed25519SecretKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String

    val publicKey: Ed25519PublicKey

    fun sign(message: String): Ed25519Signature
}