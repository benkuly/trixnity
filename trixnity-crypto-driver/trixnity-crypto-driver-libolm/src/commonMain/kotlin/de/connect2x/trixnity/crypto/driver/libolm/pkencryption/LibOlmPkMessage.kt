package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519PublicKey
import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessage
import de.connect2x.trixnity.libolm.OlmPkMessage
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPkMessage(internal val inner: OlmPkMessage) : PkMessage {

    override val ciphertext: ByteArray
        get() = inner.cipherText.decodeUnpaddedBase64Bytes()

    override val mac: ByteArray
        get() = inner.mac.decodeUnpaddedBase64Bytes()

    override val ephemeralKey: LibOlmCurve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.ephemeralKey)

    override fun close() {}

}