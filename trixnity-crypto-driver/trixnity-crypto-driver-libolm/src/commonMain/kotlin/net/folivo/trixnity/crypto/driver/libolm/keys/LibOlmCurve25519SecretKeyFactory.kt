package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKeyFactory
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmCurve25519SecretKeyFactory : Curve25519SecretKeyFactory {
    override fun invoke(): LibOlmCurve25519SecretKey = LibOlmCurve25519SecretKey(OlmPkDecryption.create())
    override fun invoke(bytes: ByteArray): LibOlmCurve25519SecretKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmCurve25519SecretKey =
        LibOlmCurve25519SecretKey(OlmPkDecryption.create(base64))
}