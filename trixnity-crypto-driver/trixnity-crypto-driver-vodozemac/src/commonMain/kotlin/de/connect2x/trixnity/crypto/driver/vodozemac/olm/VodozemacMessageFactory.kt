package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.MessageFactory

object VodozemacMessageFactory : MessageFactory {
    override val normal: VodozemacNormalMessageFactory = VodozemacNormalMessageFactory
    override val preKey: VodozemacPreKeyMessageFactory = VodozemacPreKeyMessageFactory
}