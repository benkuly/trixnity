package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519SecretKeyFactory
import de.connect2x.trixnity.libolm.OlmPkDecryption
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmCurve25519SecretKeyFactory : Curve25519SecretKeyFactory {
    override fun invoke(): LibOlmCurve25519SecretKey = LibOlmCurve25519SecretKey(OlmPkDecryption.create())
    override fun invoke(bytes: ByteArray): LibOlmCurve25519SecretKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmCurve25519SecretKey =
        LibOlmCurve25519SecretKey(OlmPkDecryption.create(base64))
}