package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKey
import net.folivo.trixnity.vodozemac.Ed25519SecretKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacEd25519SecretKey(val inner: Inner) : Ed25519SecretKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override val publicKey: LibVodozemacEd25519PublicKey
        get() = LibVodozemacEd25519PublicKey(inner.publicKey)

    override fun sign(message: String): LibVodozemacEd25519Signature
        = LibVodozemacEd25519Signature(inner.sign(message))

    override fun close()
        = inner.close()
}