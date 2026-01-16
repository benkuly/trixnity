package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.olm.Session
import de.connect2x.trixnity.vodozemac.olm.OlmMessage
import de.connect2x.trixnity.vodozemac.olm.Session as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacSession(val inner: Inner) : Session {

    override val sessionId: String
        get() = inner.sessionId

    override val hasReceivedMessage: Boolean
        get() = inner.hasReceivedMessage

    override fun encrypt(plaintext: String): Message {
        return when (val message = inner.encrypt(plaintext)) {
            is OlmMessage.PreKey.Text -> VodozemacPreKeyMessage(message)
            is OlmMessage.Normal.Text -> VodozemacNormalMessage(message)
        }
    }

    override fun decrypt(message: Message): String {
        require(message is VodozemacPreKeyMessage || message is VodozemacNormalMessage)

        val message = when (message) {
            is VodozemacPreKeyMessage -> message.inner
            is VodozemacNormalMessage -> message.inner
            else -> error("unreachable")
        }

        return inner.decrypt(message)
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.close()
}