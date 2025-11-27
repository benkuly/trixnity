package net.folivo.trixnity.crypto.driver.vodozemac.megolm

import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.megolm.SessionKey as Inner

@JvmInline
value class VodozemacSessionKey(val inner: Inner) : SessionKey {

    override val bytes: ByteArray
        get() = inner.bytes

    override val base64: String
        get() = inner.base64

    override fun close() = inner.close()

}