package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.SessionKeyFactory
import net.folivo.trixnity.vodozemac.megolm.SessionKey

object LibVodozemacSessionKeyFactory : SessionKeyFactory {
    override fun invoke(bytes: ByteArray) : LibVodozemacSessionKey
        = LibVodozemacSessionKey(SessionKey(bytes))

    override fun invoke(base64: String) : LibVodozemacSessionKey
        = LibVodozemacSessionKey(SessionKey(base64))
}