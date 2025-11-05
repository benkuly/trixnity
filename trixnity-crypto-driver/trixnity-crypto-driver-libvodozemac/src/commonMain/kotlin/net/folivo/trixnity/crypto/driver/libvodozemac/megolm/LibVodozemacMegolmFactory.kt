package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmFactory

object LibVodozemacMegolmFactory : MegolmFactory {
    override val groupSession: LibVodozemacGroupSessionFactory = LibVodozemacGroupSessionFactory
    override val inboundGroupSession: LibVodozemacInboundGroupSessionFactory = LibVodozemacInboundGroupSessionFactory

    override val sessionKey: LibVodozemacSessionKeyFactory = LibVodozemacSessionKeyFactory
    override val exportedSessionKey: LibVodozemacExportedSessionKeyFactory = LibVodozemacExportedSessionKeyFactory

    override val message: LibVodozemacMegolmMessageFactory = LibVodozemacMegolmMessageFactory
}

