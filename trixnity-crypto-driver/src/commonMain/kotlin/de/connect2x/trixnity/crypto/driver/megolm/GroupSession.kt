package de.connect2x.trixnity.crypto.driver.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface GroupSession : AutoCloseable {
    val sessionId: String
    val messageIndex: Int
    val sessionKey: SessionKey

    fun encrypt(plaintext: String): MegolmMessage

    fun pickle(pickleKey: PickleKey? = null): String
}