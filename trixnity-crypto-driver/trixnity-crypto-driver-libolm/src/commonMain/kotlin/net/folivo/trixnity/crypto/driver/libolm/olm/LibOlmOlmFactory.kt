package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.libolm.megolm.LibOlmMegolmMessageFactory
import net.folivo.trixnity.crypto.driver.olm.AccountFactory
import net.folivo.trixnity.crypto.driver.olm.MessageFactory
import net.folivo.trixnity.crypto.driver.olm.OlmFactory

object LibOlmOlmFactory : OlmFactory {

    override val account: LibOlmAccountFactory = LibOlmAccountFactory
    override val session: LibOlmSessionFactory = LibOlmSessionFactory
    override val message: LibOlmMessageFactory = LibOlmMessageFactory
}