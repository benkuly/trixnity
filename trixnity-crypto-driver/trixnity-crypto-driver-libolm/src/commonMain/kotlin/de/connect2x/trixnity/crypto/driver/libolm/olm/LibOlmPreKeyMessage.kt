package de.connect2x.trixnity.crypto.driver.libolm.olm

import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPreKeyMessage(
    internal val inner: String,
) : Message.PreKey {

    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun close() {}
}