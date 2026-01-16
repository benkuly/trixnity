package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKey
import kotlin.jvm.JvmInline

import de.connect2x.trixnity.vodozemac.megolm.ExportedSessionKey as Inner

@JvmInline
value class VodozemacExportedSessionKey(val inner: Inner) : ExportedSessionKey {
    override val bytes: ByteArray
        get() = inner.bytes

    override val base64: String
        get() = inner.base64

    override fun close() = inner.close()
}