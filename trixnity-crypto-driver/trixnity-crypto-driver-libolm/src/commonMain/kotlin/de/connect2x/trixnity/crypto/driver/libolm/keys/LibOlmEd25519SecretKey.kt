package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Ed25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Ed25519SecretKey
import de.connect2x.trixnity.crypto.driver.keys.Ed25519Signature
import de.connect2x.trixnity.libolm.OlmPkSigning
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmEd25519SecretKey(private val inner: OlmPkSigning) : Ed25519SecretKey {

    override val base64: String
        get() = inner.privateKey

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override val publicKey: Ed25519PublicKey
        get() = LibOlmEd25519PublicKey(inner.publicKey)

    override fun sign(message: String): Ed25519Signature = LibOlmEd25519Signature(inner.sign(message))

    override fun close() = inner.free()
}