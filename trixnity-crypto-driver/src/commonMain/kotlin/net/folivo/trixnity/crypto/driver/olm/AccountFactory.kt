package net.folivo.trixnity.crypto.driver.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface AccountFactory {
    operator fun invoke(): Account
    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Account
}