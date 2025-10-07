package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessage
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryptionMessage.Text as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacPkMessage(val inner: Inner) : PkMessage {

    override val ciphertext: ByteArray
        get() = inner.ciphertext

    override val mac: ByteArray
        get() = inner.mac

    override val ephemeralKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.ephemeralKey)

    override fun close()
        = inner.ephemeralKey.close()
}