package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.megolm.SessionKeyFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmSessionKeyFactory : SessionKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmSessionKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmSessionKey = LibOlmSessionKey(base64)
}