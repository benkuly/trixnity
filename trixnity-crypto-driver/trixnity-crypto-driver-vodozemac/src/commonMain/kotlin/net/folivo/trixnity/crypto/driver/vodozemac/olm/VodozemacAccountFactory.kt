package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.olm.AccountFactory
import net.folivo.trixnity.vodozemac.olm.Account

object VodozemacAccountFactory : AccountFactory {
    override fun invoke(): VodozemacAccount = VodozemacAccount(Account())

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): VodozemacAccount {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacAccount(Account.fromPickle(pickle, pickleKey?.inner))
    }
}