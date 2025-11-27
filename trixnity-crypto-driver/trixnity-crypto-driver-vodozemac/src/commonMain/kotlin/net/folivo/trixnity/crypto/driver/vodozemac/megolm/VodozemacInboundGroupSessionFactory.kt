package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKey
import net.folivo.trixnity.crypto.driver.megolm.InboundGroupSessionFactory
import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import net.folivo.trixnity.vodozemac.megolm.InboundGroupSession

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