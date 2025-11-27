package net.folivo.trixnity.crypto.driver.vodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkEncryption
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryption as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPkEncryption(val inner: Inner) : PkEncryption {

    override fun encrypt(plaintext: String): VodozemacPkMessage = VodozemacPkMessage(inner.encrypt(plaintext))

    override fun close() = inner.close()
}