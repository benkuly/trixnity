package net.folivo.trixnity.crypto.driver.keys


interface Ed25519PublicKey : AutoCloseable {
    val bytes: ByteArray
    val base64: String

    fun verify(message: String, signature: Ed25519Signature)
}