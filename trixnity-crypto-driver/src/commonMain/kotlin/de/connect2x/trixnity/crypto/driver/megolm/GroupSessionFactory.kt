package de.connect2x.trixnity.crypto.driver.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface GroupSessionFactory {
    operator fun invoke(): GroupSession

    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): GroupSession
}