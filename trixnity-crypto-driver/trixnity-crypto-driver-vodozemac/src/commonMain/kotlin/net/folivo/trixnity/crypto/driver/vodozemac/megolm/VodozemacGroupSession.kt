package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSession
import net.folivo.trixnity.crypto.driver.megolm.MegolmMessage
import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.megolm.GroupSession as Inner

@JvmInline
value class VodozemacGroupSession(val inner: Inner) : GroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val messageIndex: Int
        get() = inner.messageIndex

    override val sessionKey: VodozemacSessionKey
        get() = VodozemacSessionKey(inner.sessionKey)

    override fun encrypt(plaintext: String): VodozemacMegolmMessage =
        VodozemacMegolmMessage(inner.encrypt(plaintext))

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.close()
}