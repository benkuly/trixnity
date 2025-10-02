package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKeyFactory
import net.folivo.trixnity.vodozemac.Curve25519PublicKey

object LibVodozemacCurve25519PublicKeyFactory : Curve25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacCurve25519PublicKey
        = LibVodozemacCurve25519PublicKey(Curve25519PublicKey(bytes))

    override fun invoke(base64: String): LibVodozemacCurve25519PublicKey
        = LibVodozemacCurve25519PublicKey(Curve25519PublicKey(base64))
}