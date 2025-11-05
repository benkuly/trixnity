package net.folivo.trixnity.crypto.driver.keys

interface Ed25519PublicKeyFactory {
    operator fun invoke(bytes: ByteArray): Ed25519PublicKey
    operator fun invoke(base64: String): Ed25519PublicKey
}