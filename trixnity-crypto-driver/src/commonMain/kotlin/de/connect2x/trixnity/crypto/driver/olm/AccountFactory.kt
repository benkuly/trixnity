package de.connect2x.trixnity.crypto.driver.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface AccountFactory {
    val dehydratedDevicesSupported: Boolean
    operator fun invoke(): Account
    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Account
    fun fromDehydratedDevice(pickle: String, nonce: String, pickleKey: PickleKey): Account
}