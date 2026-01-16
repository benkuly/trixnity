package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKeyFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmCurve25519PublicKeyFactory : Curve25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmCurve25519PublicKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmCurve25519PublicKey = LibOlmCurve25519PublicKey(base64)
}