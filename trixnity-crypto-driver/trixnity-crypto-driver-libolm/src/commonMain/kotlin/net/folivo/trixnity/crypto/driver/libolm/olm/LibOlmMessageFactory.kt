package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.olm.MessageFactory

object LibOlmMessageFactory : MessageFactory {
    override val normal: LibOlmNormalMessageFactory = LibOlmNormalMessageFactory
    override val preKey: LibOlmPreKeyMessageFactory = LibOlmPreKeyMessageFactory
}