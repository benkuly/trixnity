package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKey
import net.folivo.trixnity.libolm.OlmPkDecryption
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
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