package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkEncryption
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryption as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacPkEncryption(val inner: Inner) : PkEncryption {

    override fun encrypt(plaintext: String): LibVodozemacPkMessage
        = LibVodozemacPkMessage(inner.encrypt(plaintext))

    override fun close()
        = inner.close()
}