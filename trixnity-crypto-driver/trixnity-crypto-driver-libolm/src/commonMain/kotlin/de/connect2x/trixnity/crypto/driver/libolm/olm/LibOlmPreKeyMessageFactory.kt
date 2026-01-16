package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.olm.PreKeyMessageFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmPreKeyMessageFactory : PreKeyMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmPreKeyMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPreKeyMessage = LibOlmPreKeyMessage(base64)
}