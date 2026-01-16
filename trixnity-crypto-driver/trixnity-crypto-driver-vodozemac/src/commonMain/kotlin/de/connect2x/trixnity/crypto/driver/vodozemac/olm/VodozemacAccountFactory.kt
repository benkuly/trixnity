package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.olm.AccountFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.vodozemac.olm.Account

object VodozemacAccountFactory : AccountFactory {
    override val dehydratedDevicesSupported = true
    override fun invoke(): VodozemacAccount = VodozemacAccount(Account())

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): VodozemacAccount {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacAccount(Account.fromPickle(pickle, pickleKey?.inner))
    }

    override fun fromDehydratedDevice(pickle: String, nonce: String, pickleKey: PickleKey): VodozemacAccount {
        require(pickleKey is VodozemacPickleKey)
        return VodozemacAccount(
            Account.fromDehydratedDevice(
                ciphertext = pickle,
                nonce = nonce,
                pickleKey = pickleKey.inner
            )
        )
    }
}