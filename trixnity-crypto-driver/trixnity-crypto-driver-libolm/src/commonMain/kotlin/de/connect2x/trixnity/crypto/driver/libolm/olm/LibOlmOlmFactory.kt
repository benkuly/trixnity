package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.libolm.megolm.LibOlmMegolmMessageFactory
import de.connect2x.trixnity.crypto.driver.olm.AccountFactory
import de.connect2x.trixnity.crypto.driver.olm.MessageFactory
import de.connect2x.trixnity.crypto.driver.olm.OlmFactory

object LibOlmOlmFactory : OlmFactory {

    override val account: LibOlmAccountFactory = LibOlmAccountFactory
    override val session: LibOlmSessionFactory = LibOlmSessionFactory
    override val message: LibOlmMessageFactory = LibOlmMessageFactory
}