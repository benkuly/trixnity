package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.NormalMessageFactory
import net.folivo.trixnity.crypto.driver.olm.PreKeyMessageFactory
import net.folivo.trixnity.vodozemac.olm.OlmMessage

object VodozemacPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacPreKeyMessage =
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(bytes))

    override fun invoke(base64: String): VodozemacPreKeyMessage =
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(base64))
}