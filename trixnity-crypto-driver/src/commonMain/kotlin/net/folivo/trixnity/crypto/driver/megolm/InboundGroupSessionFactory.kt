package net.folivo.trixnity.crypto.driver.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface InboundGroupSessionFactory {
    operator fun invoke(
        sessionKey: SessionKey,
    ): InboundGroupSession

    fun import(
        sessionKey: ExportedSessionKey,
    ): InboundGroupSession

    fun fromPickle(pickle: String, pickleKey: PickleKey? = null): InboundGroupSession
}