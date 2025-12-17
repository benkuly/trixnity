package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.SessionKeyFactory
import net.folivo.trixnity.vodozemac.megolm.SessionKey

object VodozemacSessionKeyFactory : SessionKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacSessionKey = VodozemacSessionKey(SessionKey(bytes))

    override fun invoke(base64: String): VodozemacSessionKey = VodozemacSessionKey(SessionKey(base64))
}