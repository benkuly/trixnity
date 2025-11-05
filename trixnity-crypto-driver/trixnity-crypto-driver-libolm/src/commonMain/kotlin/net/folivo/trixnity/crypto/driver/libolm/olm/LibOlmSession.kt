package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.Session
import net.folivo.trixnity.olm.OlmMessage
import net.folivo.trixnity.olm.OlmSession
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmSession(private val inner: OlmSession) : Session {

    override val sessionId: String
        get() = inner.sessionId

    override val hasReceivedMessage: Boolean
        get() = inner.hasReceivedMessage

    override fun encrypt(plaintext: String): Message {
        val result = inner.encrypt(plaintext)

        return when (result.type) {
            OlmMessage.OlmMessageType.INITIAL_PRE_KEY -> LibOlmPreKeyMessage(
                inner = result.cipherText,
            )

            OlmMessage.OlmMessageType.ORDINARY -> LibOlmNormalMessage(
                inner = result.cipherText,
            )
        }
    }

    override fun decrypt(message: Message): String {
        require(message is LibOlmPreKeyMessage || message is LibOlmNormalMessage)

        val message = OlmMessage(
            message.base64, when (message) {
                is Message.PreKey -> OlmMessage.OlmMessageType.INITIAL_PRE_KEY
                is Message.Normal -> OlmMessage.OlmMessageType.ORDINARY
            }
        )

        return inner.decrypt(message)
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.free()
}