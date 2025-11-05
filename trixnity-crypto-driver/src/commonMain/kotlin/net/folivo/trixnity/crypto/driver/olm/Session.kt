package net.folivo.trixnity.crypto.driver.olm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface Session : AutoCloseable {
    val sessionId: String
    val hasReceivedMessage: Boolean

    fun encrypt(plaintext: String): Message
    fun decrypt(message: Message): String
    fun pickle(pickleKey: PickleKey? = null): String
}