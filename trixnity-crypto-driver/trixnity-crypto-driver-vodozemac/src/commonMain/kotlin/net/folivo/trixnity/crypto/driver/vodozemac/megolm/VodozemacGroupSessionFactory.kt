package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSessionFactory
import net.folivo.trixnity.vodozemac.megolm.GroupSession

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