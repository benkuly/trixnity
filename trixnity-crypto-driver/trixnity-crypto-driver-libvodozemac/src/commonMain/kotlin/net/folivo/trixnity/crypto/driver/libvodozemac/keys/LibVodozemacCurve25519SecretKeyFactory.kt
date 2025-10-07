package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKeyFactory
import net.folivo.trixnity.vodozemac.Curve25519SecretKey

object LibVodozemacCurve25519SecretKeyFactory : Curve25519SecretKeyFactory {
    override fun invoke(): LibVodozemacCurve25519SecretKey
        = LibVodozemacCurve25519SecretKey(Curve25519SecretKey())

    override fun invoke(bytes: ByteArray): LibVodozemacCurve25519SecretKey
        = LibVodozemacCurve25519SecretKey(Curve25519SecretKey(bytes))

    override fun invoke(base64: String): LibVodozemacCurve25519SecretKey
        = LibVodozemacCurve25519SecretKey(Curve25519SecretKey(base64))
}