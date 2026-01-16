package de.connect2x.trixnity.crypto.driver.keys

interface Ed25519SignatureFactory {
    operator fun invoke(bytes: ByteArray): Ed25519Signature
    operator fun invoke(base64: String): Ed25519Signature
}