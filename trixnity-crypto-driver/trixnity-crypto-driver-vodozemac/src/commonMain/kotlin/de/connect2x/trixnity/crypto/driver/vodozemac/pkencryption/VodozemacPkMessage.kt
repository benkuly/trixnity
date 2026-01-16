package de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption

import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519PublicKey
import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessage
import de.connect2x.trixnity.vodozemac.pkencryption.PkEncryptionMessage.Text as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPkMessage(val inner: Inner) : PkMessage {

    override val ciphertext: ByteArray
        get() = inner.ciphertext

    override val mac: ByteArray
        get() = inner.mac

    override val ephemeralKey: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.ephemeralKey)

    override fun close() = inner.ephemeralKey.close()
}