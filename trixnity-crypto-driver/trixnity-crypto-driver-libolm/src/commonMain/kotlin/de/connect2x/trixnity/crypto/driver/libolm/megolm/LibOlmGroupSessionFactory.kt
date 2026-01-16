package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.megolm.GroupSessionFactory
import de.connect2x.trixnity.libolm.OlmOutboundGroupSession

object LibOlmGroupSessionFactory : GroupSessionFactory {
    override fun invoke(): LibOlmGroupSession = LibOlmGroupSession(OlmOutboundGroupSession.create())

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibOlmGroupSession {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return LibOlmGroupSession(
            OlmOutboundGroupSession.unpickle(pickleKey?.inner, pickle)
        )
    }
}