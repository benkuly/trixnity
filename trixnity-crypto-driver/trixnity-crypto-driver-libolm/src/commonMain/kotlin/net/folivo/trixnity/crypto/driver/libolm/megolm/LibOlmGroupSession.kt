package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.megolm.GroupSession
import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import net.folivo.trixnity.libolm.OlmOutboundGroupSession
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmGroupSession(private val inner: OlmOutboundGroupSession) : GroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val messageIndex: Int
        get() = inner.messageIndex.toInt()

    override val sessionKey: SessionKey
        get() = LibOlmSessionKey(inner.sessionKey)

    override fun encrypt(plaintext: String): LibOlmMegolmMessage = LibOlmMegolmMessage(inner.encrypt(plaintext))

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.free()
}