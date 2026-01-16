package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmCurve25519PublicKey(internal val inner: String) : Curve25519PublicKey {

    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}
}

