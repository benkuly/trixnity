package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.MegolmMessage
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.megolm.MegolmMessage.Text as Inner

@JvmInline
value class VodozemacMegolmMessage(val inner: Inner) : MegolmMessage {
    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.close()
}