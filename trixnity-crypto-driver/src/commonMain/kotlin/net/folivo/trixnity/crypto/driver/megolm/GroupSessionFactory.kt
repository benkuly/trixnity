package net.folivo.trixnity.crypto.driver.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface GroupSessionFactory {
    operator fun invoke(): GroupSession

    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): GroupSession
}