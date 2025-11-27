package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkEncryption
import net.folivo.trixnity.libolm.OlmPkEncryption
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPkEncryption(private val inner: OlmPkEncryption) : PkEncryption {

    override fun encrypt(plaintext: String): LibOlmPkMessage = LibOlmPkMessage(inner.encrypt(plaintext))

    override fun close() = inner.free()
}