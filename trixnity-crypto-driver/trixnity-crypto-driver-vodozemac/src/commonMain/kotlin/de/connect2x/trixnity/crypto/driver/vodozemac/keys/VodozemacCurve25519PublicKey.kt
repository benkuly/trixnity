package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.Curve25519PublicKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacCurve25519PublicKey(val inner: Inner) : Curve25519PublicKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.close()
}

