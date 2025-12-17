package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519Signature
import net.folivo.trixnity.crypto.driver.vodozemac.rethrow
import net.folivo.trixnity.vodozemac.Ed25519PublicKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacEd25519PublicKey(val inner: Inner) : Ed25519PublicKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun verify(message: String, signature: Ed25519Signature) {
        check(signature is VodozemacEd25519Signature)

        rethrow { inner.verify(message, signature.inner) }
    }

    override fun close() = inner.close()
}