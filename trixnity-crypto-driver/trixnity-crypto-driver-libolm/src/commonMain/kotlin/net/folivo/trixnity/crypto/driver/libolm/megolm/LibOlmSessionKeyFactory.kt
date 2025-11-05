package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.SessionKeyFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmSessionKeyFactory : SessionKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmSessionKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmSessionKey = LibOlmSessionKey(base64)
}