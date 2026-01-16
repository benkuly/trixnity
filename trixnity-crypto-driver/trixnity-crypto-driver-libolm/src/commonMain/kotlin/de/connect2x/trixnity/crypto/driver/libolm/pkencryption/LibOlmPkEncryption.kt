package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkEncryption
import de.connect2x.trixnity.libolm.OlmPkEncryption
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPkEncryption(private val inner: OlmPkEncryption) : PkEncryption {

    override fun encrypt(plaintext: String): LibOlmPkMessage = LibOlmPkMessage(inner.encrypt(plaintext))

    override fun close() = inner.free()
}