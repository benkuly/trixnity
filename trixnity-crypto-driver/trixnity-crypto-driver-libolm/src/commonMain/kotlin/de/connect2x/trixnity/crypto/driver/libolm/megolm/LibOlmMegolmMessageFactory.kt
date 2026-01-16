package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessageFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmMegolmMessageFactory : MegolmMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmMegolmMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmMegolmMessage = LibOlmMegolmMessage(base64)
}