package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmMessageFactory
import net.folivo.trixnity.vodozemac.megolm.MegolmMessage

object LibVodozemacMegolmMessageFactory : MegolmMessageFactory {
    override fun invoke(bytes: ByteArray) : LibVodozemacMegolmMessage
        = LibVodozemacMegolmMessage(MegolmMessage.Text(bytes))

    override fun invoke(base64: String) : LibVodozemacMegolmMessage
        = LibVodozemacMegolmMessage(MegolmMessage.Text(base64))
}