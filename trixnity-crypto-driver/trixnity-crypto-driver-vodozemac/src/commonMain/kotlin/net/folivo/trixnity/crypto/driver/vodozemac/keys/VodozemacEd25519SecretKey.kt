package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKey
import net.folivo.trixnity.vodozemac.Ed25519SecretKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacEd25519SecretKey(val inner: Inner) : Ed25519SecretKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override val publicKey: VodozemacEd25519PublicKey
        get() = VodozemacEd25519PublicKey(inner.publicKey)

    override fun sign(message: String): VodozemacEd25519Signature = VodozemacEd25519Signature(inner.sign(message))

    override fun close() = inner.close()
}