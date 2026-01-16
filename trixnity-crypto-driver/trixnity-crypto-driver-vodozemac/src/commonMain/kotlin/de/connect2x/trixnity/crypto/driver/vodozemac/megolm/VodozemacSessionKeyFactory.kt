package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.megolm.SessionKeyFactory
import de.connect2x.trixnity.vodozemac.megolm.SessionKey

object VodozemacSessionKeyFactory : SessionKeyFactory {
    override fun invoke(bytes: ByteArray): VodozemacSessionKey = VodozemacSessionKey(SessionKey(bytes))

    override fun invoke(base64: String): VodozemacSessionKey = VodozemacSessionKey(SessionKey(base64))
}