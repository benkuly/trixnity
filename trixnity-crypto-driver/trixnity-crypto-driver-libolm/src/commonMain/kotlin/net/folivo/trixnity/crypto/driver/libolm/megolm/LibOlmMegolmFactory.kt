package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmFactory

object LibOlmMegolmFactory : MegolmFactory {

    override val sessionKey: LibOlmSessionKeyFactory = LibOlmSessionKeyFactory
    override val exportedSessionKey: LibOlmExportedSessionKeyFactory = LibOlmExportedSessionKeyFactory

    override val groupSession: LibOlmGroupSessionFactory = LibOlmGroupSessionFactory
    override val inboundGroupSession: LibOlmInboundGroupSessionFactory = LibOlmInboundGroupSessionFactory

    override val message: LibOlmMegolmMessageFactory = LibOlmMegolmMessageFactory
}