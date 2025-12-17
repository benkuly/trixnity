package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.olm.SessionFactory
import net.folivo.trixnity.libolm.OlmSession

object LibOlmSessionFactory : SessionFactory {
    override fun fromPickle(
        pickle: String, pickleKey: PickleKey?
    ): LibOlmSession {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return LibOlmSession(
            OlmSession.unpickle(
                pickleKey?.inner, pickle
            )
        )
    }
}