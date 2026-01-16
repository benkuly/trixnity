package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.megolm.GroupSession
import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessage
import de.connect2x.trixnity.crypto.driver.megolm.SessionKey
import kotlin.jvm.JvmInline
import de.connect2x.trixnity.vodozemac.megolm.GroupSession as Inner

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