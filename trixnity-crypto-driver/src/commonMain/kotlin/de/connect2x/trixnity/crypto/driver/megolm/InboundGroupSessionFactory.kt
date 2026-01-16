package de.connect2x.trixnity.crypto.driver.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface InboundGroupSessionFactory {
    operator fun invoke(
        sessionKey: SessionKey,
    ): InboundGroupSession

    fun import(
        sessionKey: ExportedSessionKey,
    ): InboundGroupSession

    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): InboundGroupSession
}