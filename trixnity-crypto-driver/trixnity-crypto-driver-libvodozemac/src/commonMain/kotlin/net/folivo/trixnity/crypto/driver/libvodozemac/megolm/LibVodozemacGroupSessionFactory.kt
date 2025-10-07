package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSessionFactory
import net.folivo.trixnity.vodozemac.megolm.GroupSession

object LibVodozemacGroupSessionFactory : GroupSessionFactory {
    override fun invoke(): LibVodozemacGroupSession
        = LibVodozemacGroupSession(GroupSession())

    override fun fromPickle(
        pickle: String,
        pickleKey: PickleKey?
    ): LibVodozemacGroupSession {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return LibVodozemacGroupSession(
            GroupSession.fromPickle(
                pickle,
                pickleKey?.inner
            )
        )
    }
}