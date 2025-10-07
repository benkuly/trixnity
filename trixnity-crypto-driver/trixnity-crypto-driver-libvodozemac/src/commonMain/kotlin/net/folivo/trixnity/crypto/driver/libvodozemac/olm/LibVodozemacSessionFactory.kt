package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.olm.SessionFactory
import net.folivo.trixnity.vodozemac.olm.Session

object LibVodozemacSessionFactory : SessionFactory {
    override fun fromPickle(pickle: String, pickleKey: PickleKey?): LibVodozemacSession {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return LibVodozemacSession(Session.fromPickle(pickle, pickleKey?.inner))
    }
}