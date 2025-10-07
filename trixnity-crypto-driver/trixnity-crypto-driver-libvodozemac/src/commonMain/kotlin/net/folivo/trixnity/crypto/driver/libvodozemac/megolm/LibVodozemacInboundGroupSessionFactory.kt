package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKey
import net.folivo.trixnity.crypto.driver.megolm.InboundGroupSessionFactory
import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import net.folivo.trixnity.vodozemac.megolm.InboundGroupSession

object LibVodozemacInboundGroupSessionFactory : InboundGroupSessionFactory {
    override fun invoke(sessionKey: SessionKey): LibVodozemacInboundGroupSession {
        require(sessionKey is LibVodozemacSessionKey)

        return LibVodozemacInboundGroupSession(InboundGroupSession(sessionKey.inner))
    }

    override fun import(sessionKey: ExportedSessionKey): LibVodozemacInboundGroupSession {
        require(sessionKey is LibVodozemacExportedSessionKey)

        return LibVodozemacInboundGroupSession(InboundGroupSession.import(sessionKey.inner))
    }

    override fun fromPickle(
        pickle: String, pickleKey: PickleKey?
    ): LibVodozemacInboundGroupSession {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return LibVodozemacInboundGroupSession(
            InboundGroupSession.fromPickle(
                pickle, pickleKey?.inner
            )
        )

    }
}