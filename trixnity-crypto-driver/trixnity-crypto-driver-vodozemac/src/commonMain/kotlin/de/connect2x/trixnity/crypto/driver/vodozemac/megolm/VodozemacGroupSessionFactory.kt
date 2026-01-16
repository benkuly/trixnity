package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.megolm.GroupSessionFactory
import de.connect2x.trixnity.vodozemac.megolm.GroupSession

object VodozemacGroupSessionFactory : GroupSessionFactory {
    override fun invoke(): VodozemacGroupSession = VodozemacGroupSession(GroupSession())

    override fun fromPickle(
        pickle: String,
        pickleKey: PickleKey?
    ): VodozemacGroupSession {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return VodozemacGroupSession(
            GroupSession.fromPickle(
                pickle,
                pickleKey?.inner
            )
        )
    }
}