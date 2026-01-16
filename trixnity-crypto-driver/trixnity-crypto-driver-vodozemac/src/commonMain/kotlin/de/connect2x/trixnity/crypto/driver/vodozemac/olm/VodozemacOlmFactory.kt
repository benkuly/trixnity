package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.OlmFactory

object VodozemacOlmFactory : OlmFactory {
    override val account: VodozemacAccountFactory = VodozemacAccountFactory
    override val message: VodozemacMessageFactory = VodozemacMessageFactory
    override val session: VodozemacSessionFactory = VodozemacSessionFactory
}