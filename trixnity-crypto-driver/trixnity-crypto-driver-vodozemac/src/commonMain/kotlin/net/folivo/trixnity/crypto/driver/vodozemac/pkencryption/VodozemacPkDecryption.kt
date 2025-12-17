package net.folivo.trixnity.crypto.driver.vodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519SecretKey
import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryption
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessage
import net.folivo.trixnity.vodozemac.pkencryption.PkDecryption as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPkDecryption(val inner: Inner) : PkDecryption {

    override val publicKey: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.publicKey)

    override val secretKey: VodozemacCurve25519SecretKey
        get() = VodozemacCurve25519SecretKey(inner.secretKey)

    override fun decrypt(message: PkMessage): String {
        require(message is VodozemacPkMessage)

        return inner.decrypt(message.inner)
    }

    override fun close() = inner.close()
}