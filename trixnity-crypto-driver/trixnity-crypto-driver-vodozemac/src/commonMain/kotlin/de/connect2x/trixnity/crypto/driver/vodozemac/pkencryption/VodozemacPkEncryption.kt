package de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkEncryption
import de.connect2x.trixnity.vodozemac.pkencryption.PkEncryption as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPkEncryption(val inner: Inner) : PkEncryption {

    override fun encrypt(plaintext: String): VodozemacPkMessage = VodozemacPkMessage(inner.encrypt(plaintext))

    override fun close() = inner.close()
}