package net.folivo.trixnity.crypto.driver.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface InboundGroupSession : AutoCloseable {
    val sessionId: String
    val firstKnownIndex: Int

    fun decrypt(message: MegolmMessage): DecryptedMessage

    fun exportAt(index: Int): ExportedSessionKey?

    fun exportAtFirstKnownIndex(): ExportedSessionKey

    fun pickle(pickleKey: PickleKey? = null): String

    data class DecryptedMessage(
        val plaintext: String,
        val messageIndex: Int,
    )
}

