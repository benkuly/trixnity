package de.connect2x.trixnity.crypto.driver.keys

interface Curve25519SecretKeyFactory {
    operator fun invoke(): Curve25519SecretKey
    operator fun invoke(bytes: ByteArray): Curve25519SecretKey
    operator fun invoke(base64: String): Curve25519SecretKey
}