package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKeyFactory
import de.connect2x.trixnity.vodozemac.Curve25519PublicKey

object VodozemacCurve25519PublicKeyFactory : Curve25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacCurve25519PublicKey =
        VodozemacCurve25519PublicKey(Curve25519PublicKey(bytes))

    override fun invoke(base64: String): VodozemacCurve25519PublicKey =
        VodozemacCurve25519PublicKey(Curve25519PublicKey(base64))
}