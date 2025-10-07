package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKey
import net.folivo.trixnity.vodozemac.Curve25519SecretKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacCurve25519SecretKey(val inner: Inner) : Curve25519SecretKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override val publicKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.publicKey)

    override fun close()
        = inner.close()
}