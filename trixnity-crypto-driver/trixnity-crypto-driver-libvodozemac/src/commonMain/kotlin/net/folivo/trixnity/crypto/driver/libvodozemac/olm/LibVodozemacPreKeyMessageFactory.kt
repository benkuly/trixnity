package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.NormalMessageFactory
import net.folivo.trixnity.crypto.driver.olm.PreKeyMessageFactory
import net.folivo.trixnity.vodozemac.olm.OlmMessage

object LibVodozemacPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacPreKeyMessage
        = LibVodozemacPreKeyMessage(OlmMessage.PreKey.Text(bytes))

    override fun invoke(base64: String): LibVodozemacPreKeyMessage
        = LibVodozemacPreKeyMessage(OlmMessage.PreKey.Text(base64))
}