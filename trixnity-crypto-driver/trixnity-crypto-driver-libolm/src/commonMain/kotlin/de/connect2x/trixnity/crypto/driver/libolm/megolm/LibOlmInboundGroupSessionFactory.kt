package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKey
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSessionFactory
import de.connect2x.trixnity.crypto.driver.megolm.SessionKey
import de.connect2x.trixnity.libolm.OlmInboundGroupSession
import de.connect2x.trixnity.libolm.OlmLibraryException

object LibOlmInboundGroupSessionFactory : InboundGroupSessionFactory {

    override fun invoke(
        sessionKey: SessionKey,
    ): LibOlmInboundGroupSession {
        require(sessionKey is LibOlmSessionKey)

        return LibOlmInboundGroupSession(
            OlmInboundGroupSession.create(sessionKey.inner)
        )
    }

    override fun import(
        sessionKey: ExportedSessionKey,
    ): LibOlmInboundGroupSession {
        require(sessionKey is LibOlmExportedSessionKey)

        return LibOlmInboundGroupSession(
            OlmInboundGroupSession.import(sessionKey.inner)
        )
    }

    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibOlmInboundGroupSession {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        try {
            return LibOlmInboundGroupSession(
                OlmInboundGroupSession.unpickle(pickleKey?.inner, pickle)
            )
        } catch (e: OlmLibraryException) {
            throw CryptoDriverException(e)
        }
    }
}