package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519SecretKeyFactory
import de.connect2x.trixnity.vodozemac.Curve25519SecretKey

object VodozemacCurve25519SecretKeyFactory : Curve25519SecretKeyFactory {
    override fun invoke(): VodozemacCurve25519SecretKey = VodozemacCurve25519SecretKey(Curve25519SecretKey())

    override fun invoke(bytes: ByteArray): VodozemacCurve25519SecretKey =
        VodozemacCurve25519SecretKey(Curve25519SecretKey(bytes))

    override fun invoke(base64: String): VodozemacCurve25519SecretKey =
        VodozemacCurve25519SecretKey(Curve25519SecretKey(base64))
}