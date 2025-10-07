package net.folivo.trixnity.crypto.driver.megolm

interface MegolmFactory {
    val sessionKey: SessionKeyFactory
    val exportedSessionKey: ExportedSessionKeyFactory

    val groupSession: GroupSessionFactory
    val inboundGroupSession: InboundGroupSessionFactory

    val message: MegolmMessageFactory
}