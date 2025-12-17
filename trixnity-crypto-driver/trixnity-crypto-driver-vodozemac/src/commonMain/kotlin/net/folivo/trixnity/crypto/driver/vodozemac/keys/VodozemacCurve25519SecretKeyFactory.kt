package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519SecretKeyFactory
import net.folivo.trixnity.vodozemac.Curve25519SecretKey

object VodozemacCurve25519SecretKeyFactory : Curve25519SecretKeyFactory {
    override fun invoke(): VodozemacCurve25519SecretKey = VodozemacCurve25519SecretKey(Curve25519SecretKey())

    override fun invoke(bytes: ByteArray): VodozemacCurve25519SecretKey =
        VodozemacCurve25519SecretKey(Curve25519SecretKey(bytes))

    override fun invoke(base64: String): VodozemacCurve25519SecretKey =
        VodozemacCurve25519SecretKey(Curve25519SecretKey(base64))
}