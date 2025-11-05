package net.folivo.trixnity.crypto.driver.libvodozemac.megolm


import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKeyFactory
import net.folivo.trixnity.vodozemac.megolm.ExportedSessionKey

object LibVodozemacExportedSessionKeyFactory : ExportedSessionKeyFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacExportedSessionKey
            = LibVodozemacExportedSessionKey(ExportedSessionKey(bytes))

    override fun invoke(base64: String): LibVodozemacExportedSessionKey
            = LibVodozemacExportedSessionKey(ExportedSessionKey(base64))
}