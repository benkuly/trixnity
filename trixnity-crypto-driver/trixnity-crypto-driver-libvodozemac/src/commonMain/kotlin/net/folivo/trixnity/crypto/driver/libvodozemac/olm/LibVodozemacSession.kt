package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.Session
import net.folivo.trixnity.vodozemac.olm.OlmMessage
import net.folivo.trixnity.vodozemac.olm.Session as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacSession(val inner: Inner) : Session {

    override val sessionId: String
        get() = inner.sessionId

    override val hasReceivedMessage: Boolean
        get() = inner.hasReceivedMessage

    override fun encrypt(plaintext: String): Message {
        return when (val message = inner.encrypt(plaintext)) {
            is OlmMessage.PreKey.Text -> LibVodozemacPreKeyMessage(message)
            is OlmMessage.Normal.Text -> LibVodozemacNormalMessage(message)
        }
    }

    override fun decrypt(message: Message): String {
        require(message is LibVodozemacPreKeyMessage || message is LibVodozemacNormalMessage)

        val message = when (message) {
            is LibVodozemacPreKeyMessage -> message.inner
            is LibVodozemacNormalMessage -> message.inner
            else -> error("unreachable")
        }

        return inner.decrypt(message)
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close()
        = inner.close()
}