package net.folivo.trixnity.crypto.driver.libvodozemac.sas

import net.folivo.trixnity.crypto.driver.sas.Mac
import net.folivo.trixnity.vodozemac.sas.Mac as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacMac(val inner: Inner) : Mac {
    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close()
        = inner.close()
}