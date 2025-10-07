package net.folivo.trixnity.crypto.driver.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface SessionFactory {
    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Session
}