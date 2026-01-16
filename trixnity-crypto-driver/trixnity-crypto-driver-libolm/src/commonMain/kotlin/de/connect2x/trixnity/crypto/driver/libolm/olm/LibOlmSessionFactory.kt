package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.olm.SessionFactory
import de.connect2x.trixnity.libolm.OlmSession

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