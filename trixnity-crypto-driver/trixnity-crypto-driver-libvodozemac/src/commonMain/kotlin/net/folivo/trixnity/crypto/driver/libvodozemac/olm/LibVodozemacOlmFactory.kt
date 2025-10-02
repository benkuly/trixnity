package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.OlmFactory

object LibVodozemacOlmFactory : OlmFactory {
    override val account: LibVodozemacAccountFactory = LibVodozemacAccountFactory
    override val message: LibVodozemacMessageFactory = LibVodozemacMessageFactory
    override val session: LibVodozemacSessionFactory = LibVodozemacSessionFactory
}