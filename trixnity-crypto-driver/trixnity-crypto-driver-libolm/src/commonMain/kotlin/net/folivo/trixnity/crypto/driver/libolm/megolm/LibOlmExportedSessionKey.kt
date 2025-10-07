package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKey
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmExportedSessionKey(internal val inner: String) : ExportedSessionKey {

    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}

}


