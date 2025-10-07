package net.folivo.trixnity.crypto.driver.libolm.megolm

import net.folivo.trixnity.crypto.driver.megolm.SessionKey
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmSessionKey(internal val inner: String) : SessionKey {

    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}

}

