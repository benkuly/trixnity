package de.connect2x.trixnity.crypto.driver.vodozemac.olm

import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.vodozemac.olm.OlmMessage.PreKey.Text as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPreKeyMessage(val inner: Inner) : Message.PreKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close() = inner.message.close()
}