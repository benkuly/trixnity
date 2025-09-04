package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkMessageFactory
import net.folivo.trixnity.olm.OlmPkMessage

object LibOlmPkMessageFactory : PkMessageFactory {
    override fun invoke(cipherText: String, mac: String, ephemeralKey: String): LibOlmPkMessage = LibOlmPkMessage(
        OlmPkMessage(
            cipherText, mac, ephemeralKey
        )
    )
}