package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessageFactory
import de.connect2x.trixnity.libolm.OlmPkMessage

object LibOlmPkMessageFactory : PkMessageFactory {
    override fun invoke(cipherText: String, mac: String, ephemeralKey: String): LibOlmPkMessage = LibOlmPkMessage(
        OlmPkMessage(
            cipherText, mac, ephemeralKey
        )
    )
}