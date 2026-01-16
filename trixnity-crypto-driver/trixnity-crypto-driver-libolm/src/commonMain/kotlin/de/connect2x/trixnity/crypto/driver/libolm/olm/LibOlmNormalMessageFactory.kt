package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.olm.NormalMessageFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmNormalMessageFactory : NormalMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmNormalMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmNormalMessage = LibOlmNormalMessage(base64)
}