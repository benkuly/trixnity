package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519Signature
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
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