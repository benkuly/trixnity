package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKey
import kotlin.jvm.JvmInline

import net.folivo.trixnity.vodozemac.megolm.ExportedSessionKey as Inner

@JvmInline
value class LibVodozemacExportedSessionKey(val inner: Inner) : ExportedSessionKey {
    override val bytes: ByteArray
        get() = inner.bytes

    override val base64: String
        get() = inner.base64

    override fun close()
        = inner.close()
}