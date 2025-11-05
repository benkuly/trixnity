package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.vodozemac.olm.OlmMessage.PreKey.Text as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacPreKeyMessage(val inner: Inner) : Message.PreKey {

    override val base64: String
        get() = inner.base64

    override val bytes: ByteArray
        get() = inner.bytes

    override fun close()
        = inner.message.close()
}