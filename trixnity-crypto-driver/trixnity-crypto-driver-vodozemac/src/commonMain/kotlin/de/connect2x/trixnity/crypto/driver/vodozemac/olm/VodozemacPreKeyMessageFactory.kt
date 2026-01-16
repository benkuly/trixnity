package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.olm.NormalMessageFactory
import de.connect2x.trixnity.crypto.driver.olm.PreKeyMessageFactory
import de.connect2x.trixnity.vodozemac.olm.OlmMessage

object VodozemacPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacPreKeyMessage =
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(bytes))

    override fun invoke(base64: String): VodozemacPreKeyMessage =
        VodozemacPreKeyMessage(OlmMessage.PreKey.Text(base64))
}