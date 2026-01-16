package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.olm.SessionFactory
import de.connect2x.trixnity.vodozemac.olm.Session

object VodozemacSessionFactory : SessionFactory {
    override fun fromPickle(pickle: String, pickleKey: PickleKey?): VodozemacSession {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacSession(Session.fromPickle(pickle, pickleKey?.inner))
    }
}