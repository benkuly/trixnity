package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.olm.NormalMessageFactory
import de.connect2x.trixnity.vodozemac.olm.OlmMessage

object VodozemacNormalMessageFactory : NormalMessageFactory {
    override fun invoke(bytes: ByteArray): VodozemacNormalMessage =
        VodozemacNormalMessage(OlmMessage.Normal.Text(bytes))

    override fun invoke(base64: String): VodozemacNormalMessage =
        VodozemacNormalMessage(OlmMessage.Normal.Text(base64))
}