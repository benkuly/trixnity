package net.folivo.trixnity.crypto.driver.olm

interface OlmFactory {
    val account: AccountFactory
    val message: MessageFactory
    val session: SessionFactory
}