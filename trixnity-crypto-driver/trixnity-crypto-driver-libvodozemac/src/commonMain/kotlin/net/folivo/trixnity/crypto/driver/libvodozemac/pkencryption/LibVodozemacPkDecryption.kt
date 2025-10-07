package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519SecretKey
import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryption
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessage
import net.folivo.trixnity.vodozemac.pkencryption.PkDecryption as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacPkDecryption(val inner: Inner) : PkDecryption {

    override val publicKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.publicKey)

    override val secretKey: LibVodozemacCurve25519SecretKey
        get() = LibVodozemacCurve25519SecretKey(inner.secretKey)

    override fun decrypt(message: PkMessage): String {
        require(message is LibVodozemacPkMessage)

        return inner.decrypt(message.inner)
    }

    override fun close()
        = inner.close()
}