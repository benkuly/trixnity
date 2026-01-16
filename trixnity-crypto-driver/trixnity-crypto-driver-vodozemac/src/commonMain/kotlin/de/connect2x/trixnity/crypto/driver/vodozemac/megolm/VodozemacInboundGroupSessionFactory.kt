package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKey
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSessionFactory
import de.connect2x.trixnity.crypto.driver.megolm.SessionKey
import de.connect2x.trixnity.vodozemac.megolm.InboundGroupSession

object VodozemacInboundGroupSessionFactory : InboundGroupSessionFactory {
    override fun invoke(sessionKey: SessionKey): VodozemacInboundGroupSession {
        require(sessionKey is VodozemacSessionKey)

        return VodozemacInboundGroupSession(InboundGroupSession(sessionKey.inner))
    }

    override fun import(sessionKey: ExportedSessionKey): VodozemacInboundGroupSession {
        require(sessionKey is VodozemacExportedSessionKey)

        return VodozemacInboundGroupSession(InboundGroupSession.import(sessionKey.inner))
    }

    override fun fromPickle(
        pickle: String, pickleKey: PickleKey?
    ): VodozemacInboundGroupSession {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacInboundGroupSession(
            InboundGroupSession.fromPickle(
                pickle, pickleKey?.inner
            )
        )

    }
}