package net.folivo.trixnity.crypto.driver.libolm.sas

import net.folivo.trixnity.crypto.driver.sas.Mac
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmMac(private val inner: String) : Mac {
    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}
}