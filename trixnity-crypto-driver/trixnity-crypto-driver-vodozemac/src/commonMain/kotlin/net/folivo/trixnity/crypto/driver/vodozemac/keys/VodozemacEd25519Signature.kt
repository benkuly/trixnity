package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519Signature
import net.folivo.trixnity.vodozemac.Ed25519Signature as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacEd25519Signature(val inner: Inner) : Ed25519Signature {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.close()
}