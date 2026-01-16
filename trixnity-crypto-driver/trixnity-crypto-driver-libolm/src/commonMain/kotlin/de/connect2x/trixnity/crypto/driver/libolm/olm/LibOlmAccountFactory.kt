package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.olm.Account
import de.connect2x.trixnity.crypto.driver.olm.AccountFactory
import de.connect2x.trixnity.libolm.OlmAccount

object LibOlmAccountFactory : AccountFactory {
    override val dehydratedDevicesSupported = false
    override fun invoke(): LibOlmAccount = LibOlmAccount(OlmAccount.create())

    override fun fromPickle(
        pickle: String, pickleKey: PickleKey?
    ): LibOlmAccount {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return LibOlmAccount(OlmAccount.unpickle(pickleKey?.inner, pickle))
    }

    override fun fromDehydratedDevice(pickle: String, nonce: String, pickleKey: PickleKey): Account {
        throw UnsupportedOperationException("libolm does not support dehydrated devices")
    }
}