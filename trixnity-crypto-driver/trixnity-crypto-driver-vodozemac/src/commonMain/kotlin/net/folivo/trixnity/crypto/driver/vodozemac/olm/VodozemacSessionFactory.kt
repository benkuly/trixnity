package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.olm.SessionFactory
import net.folivo.trixnity.vodozemac.olm.Session

object VodozemacSessionFactory : SessionFactory {
    override fun fromPickle(pickle: String, pickleKey: PickleKey?): VodozemacSession {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacSession(Session.fromPickle(pickle, pickleKey?.inner))
    }
}