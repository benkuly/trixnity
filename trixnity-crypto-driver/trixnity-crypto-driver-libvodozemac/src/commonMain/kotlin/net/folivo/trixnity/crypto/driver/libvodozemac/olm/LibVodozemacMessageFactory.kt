package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.MessageFactory

object LibVodozemacMessageFactory : MessageFactory {
    override val normal: LibVodozemacNormalMessageFactory = LibVodozemacNormalMessageFactory
    override val preKey: LibVodozemacPreKeyMessageFactory = LibVodozemacPreKeyMessageFactory
}