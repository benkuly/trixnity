package net.folivo.trixnity.crypto.driver.keys

interface Curve25519PublicKeyFactory {
    operator fun invoke(bytes: ByteArray): Curve25519PublicKey
    operator fun invoke(base64: String): Curve25519PublicKey
}