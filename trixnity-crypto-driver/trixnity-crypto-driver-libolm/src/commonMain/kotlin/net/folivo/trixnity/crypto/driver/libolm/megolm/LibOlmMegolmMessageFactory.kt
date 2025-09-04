package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmMessageFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmMegolmMessageFactory : MegolmMessageFactory {
    override fun invoke(bytes: ByteArray): LibOlmMegolmMessage = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmMegolmMessage = LibOlmMegolmMessage(base64)
}