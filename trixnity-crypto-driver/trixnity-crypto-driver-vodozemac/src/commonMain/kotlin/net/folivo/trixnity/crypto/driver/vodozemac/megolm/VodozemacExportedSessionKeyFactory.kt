package net.folivo.trixnity.crypto.driver.vodozemac.megolm


import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKeyFactory
import net.folivo.trixnity.vodozemac.megolm.ExportedSessionKey

object VodozemacExportedSessionKeyFactory : ExportedSessionKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacExportedSessionKey =
        VodozemacExportedSessionKey(ExportedSessionKey(bytes))

    override fun invoke(base64: String): VodozemacExportedSessionKey =
        VodozemacExportedSessionKey(ExportedSessionKey(base64))
}