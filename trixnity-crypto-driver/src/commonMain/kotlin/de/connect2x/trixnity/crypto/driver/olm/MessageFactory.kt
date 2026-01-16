package de.connect2x.trixnity.crypto.driver.olm

interface MessageFactory {
    val normal: NormalMessageFactory
    val preKey: PreKeyMessageFactory
}