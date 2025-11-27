package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.OlmFactory

object VodozemacOlmFactory : OlmFactory {
    override val account: VodozemacAccountFactory = VodozemacAccountFactory
    override val message: VodozemacMessageFactory = VodozemacMessageFactory
    override val session: VodozemacSessionFactory = VodozemacSessionFactory
}