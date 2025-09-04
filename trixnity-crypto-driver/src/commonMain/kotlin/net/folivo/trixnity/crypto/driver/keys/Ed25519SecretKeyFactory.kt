package net.folivo.trixnity.crypto.driver.keys

interface Ed25519SecretKeyFactory {
    operator fun invoke(): Ed25519SecretKey
    operator fun invoke(bytes: ByteArray): Ed25519SecretKey
    operator fun invoke(base64: String): Ed25519SecretKey
}