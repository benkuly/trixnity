package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.NormalMessageFactory
import net.folivo.trixnity.vodozemac.olm.OlmMessage

object LibVodozemacNormalMessageFactory : NormalMessageFactory {
    override fun invoke(bytes: ByteArray): LibVodozemacNormalMessage
        = LibVodozemacNormalMessage(OlmMessage.Normal.Text(bytes))

    override fun invoke(base64: String): LibVodozemacNormalMessage
        = LibVodozemacNormalMessage(OlmMessage.Normal.Text(base64))
}