package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Curve25519SecretKey
import de.connect2x.trixnity.libolm.OlmPkDecryption
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmCurve25519SecretKey(private val inner: OlmPkDecryption) : Curve25519SecretKey {

    override val base64: String
        get() = inner.privateKey

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override val publicKey: Curve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.publicKey)

    override fun close() = inner.free()
}