package de.connect2x.trixnity.crypto.driver.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface SessionFactory {
    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Session
}