package de.connect2x.trixnity.crypto.driver.vodozemac.megolm


import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKeyFactory
import de.connect2x.trixnity.vodozemac.megolm.ExportedSessionKey

object VodozemacExportedSessionKeyFactory : ExportedSessionKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacExportedSessionKey =
        VodozemacExportedSessionKey(ExportedSessionKey(bytes))

    override fun invoke(base64: String): VodozemacExportedSessionKey =
        VodozemacExportedSessionKey(ExportedSessionKey(base64))
}