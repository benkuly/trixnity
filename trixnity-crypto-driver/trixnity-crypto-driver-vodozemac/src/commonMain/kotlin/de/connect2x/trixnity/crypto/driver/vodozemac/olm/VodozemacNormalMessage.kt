package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.vodozemac.olm.OlmMessage.Normal.Text as Inner
import de.connect2x.trixnity.crypto.driver.olm.Message
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacNormalMessage(val inner: Inner) : Message.Normal {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.message.close()
}