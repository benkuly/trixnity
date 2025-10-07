package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.olm.NormalMessageFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmNormalMessageFactory : NormalMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmNormalMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmNormalMessage = LibOlmNormalMessage(base64)
}