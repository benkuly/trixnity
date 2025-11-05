package net.folivo.trixnity.crypto.driver.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey

interface GroupSession : AutoCloseable {
    val sessionId: String
    val messageIndex: Int
    val sessionKey: SessionKey

    fun encrypt(plaintext: String): MegolmMessage

    fun pickle(pickleKey: PickleKey? = null): String
}