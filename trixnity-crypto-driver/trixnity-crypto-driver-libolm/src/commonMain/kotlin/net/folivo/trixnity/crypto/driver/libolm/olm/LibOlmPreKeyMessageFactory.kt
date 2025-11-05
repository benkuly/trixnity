package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.olm.PreKeyMessageFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmPreKeyMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPreKeyMessage = LibOlmPreKeyMessage(base64)
}