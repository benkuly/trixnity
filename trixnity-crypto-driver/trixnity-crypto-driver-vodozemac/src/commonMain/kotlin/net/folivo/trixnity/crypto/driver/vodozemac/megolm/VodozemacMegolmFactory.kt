package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmFactory

object VodozemacMegolmFactory : MegolmFactory {
    override val groupSession: VodozemacGroupSessionFactory = VodozemacGroupSessionFactory
    override val inboundGroupSession: VodozemacInboundGroupSessionFactory = VodozemacInboundGroupSessionFactory

    override val sessionKey: VodozemacSessionKeyFactory = VodozemacSessionKeyFactory
    override val exportedSessionKey: VodozemacExportedSessionKeyFactory = VodozemacExportedSessionKeyFactory

    override val message: VodozemacMegolmMessageFactory = VodozemacMegolmMessageFactory
}

