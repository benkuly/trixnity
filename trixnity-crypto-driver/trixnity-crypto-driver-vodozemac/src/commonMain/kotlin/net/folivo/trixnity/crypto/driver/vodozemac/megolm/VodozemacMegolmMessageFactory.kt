package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmMessageFactory
import net.folivo.trixnity.vodozemac.megolm.MegolmMessage

object VodozemacMegolmMessageFactory : MegolmMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacMegolmMessage =
        VodozemacMegolmMessage(MegolmMessage.Text(bytes))

    override fun invoke(base64: String): VodozemacMegolmMessage =
        VodozemacMegolmMessage(MegolmMessage.Text(base64))
}