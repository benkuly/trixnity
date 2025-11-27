package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.MessageFactory

object VodozemacMessageFactory : MessageFactory {
    override val normal: VodozemacNormalMessageFactory = VodozemacNormalMessageFactory
    override val preKey: VodozemacPreKeyMessageFactory = VodozemacPreKeyMessageFactory
}