package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSession
import net.folivo.trixnity.crypto.driver.megolm.MegolmMessage
import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.megolm.GroupSession as Inner

@JvmInline
value class LibVodozemacGroupSession(val inner: Inner) : GroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val messageIndex: Int
        get() = inner.messageIndex

    override val sessionKey: LibVodozemacSessionKey
        get() = LibVodozemacSessionKey(inner.sessionKey)

    override fun encrypt(plaintext: String): LibVodozemacMegolmMessage
            = LibVodozemacMegolmMessage(inner.encrypt(plaintext))

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close()
        = inner.close()
}