package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKeyFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmExportedSessionKeyFactory : ExportedSessionKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmExportedSessionKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmExportedSessionKey = LibOlmExportedSessionKey(base64)
}