package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.olm.AccountFactory
import net.folivo.trixnity.olm.OlmAccount

object LibOlmAccountFactory : AccountFactory {
    override fun invoke(): LibOlmAccount = LibOlmAccount(OlmAccount.create())

    override fun fromPickle(
        pickle: String, pickleKey: PickleKey?
    ): LibOlmAccount {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return LibOlmAccount(OlmAccount.unpickle(pickleKey?.inner, pickle))
    }
}