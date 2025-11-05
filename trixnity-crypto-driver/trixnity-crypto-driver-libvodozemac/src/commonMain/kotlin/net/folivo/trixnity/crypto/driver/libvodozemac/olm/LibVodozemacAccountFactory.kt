package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.olm.AccountFactory
import net.folivo.trixnity.vodozemac.olm.Account

object LibVodozemacAccountFactory : AccountFactory {
    override fun invoke(): LibVodozemacAccount
        = LibVodozemacAccount(Account())

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibVodozemacAccount {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return LibVodozemacAccount(Account.fromPickle(pickle, pickleKey?.inner))
    }
}