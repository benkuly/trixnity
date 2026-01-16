package de.connect2x.trixnity.crypto.driver.libolm.sas

import de.connect2x.trixnity.crypto.driver.sas.Mac
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmMac(private val inner: String) : Mac {
    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}
}