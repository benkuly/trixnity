package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.olm.MessageFactory

object LibOlmMessageFactory : MessageFactory {
    override val normal: LibOlmNormalMessageFactory = LibOlmNormalMessageFactory
    override val preKey: LibOlmPreKeyMessageFactory = LibOlmPreKeyMessageFactory
}