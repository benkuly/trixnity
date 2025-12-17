package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSessionFactory
import net.folivo.trixnity.libolm.OlmOutboundGroupSession

object LibOlmGroupSessionFactory : GroupSessionFactory {
    override fun invoke(): LibOlmGroupSession = LibOlmGroupSession(OlmOutboundGroupSession.create())

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibOlmGroupSession {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return LibOlmGroupSession(
            OlmOutboundGroupSession.unpickle(pickleKey?.inner, pickle)
        )
    }
}