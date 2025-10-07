package net.folivo.trixnity.crypto.driver.olm

interface MessageFactory {
    val normal: NormalMessageFactory
    val preKey: PreKeyMessageFactory
}