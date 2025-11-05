package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKeyFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmExportedSessionKeyFactory : ExportedSessionKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmExportedSessionKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmExportedSessionKey = LibOlmExportedSessionKey(base64)
}