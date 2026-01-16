package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Ed25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Ed25519Signature
import de.connect2x.trixnity.crypto.driver.libolm.rethrow
import de.connect2x.trixnity.libolm.OlmUtility
import de.connect2x.trixnity.libolm.freeAfter
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmEd25519PublicKey(private val inner: String) : Ed25519PublicKey {

    override val base64: String
        get() = inner

    override val bytes: ByteArray
        get() = base64.decodeUnpaddedBase64Bytes()

    override fun verify(message: String, signature: Ed25519Signature) {
        check(signature is LibOlmEd25519Signature)

        rethrow {
            freeAfter(OlmUtility.create()) { utility ->
                utility.verifyEd25519(inner, message, signature.inner)
            }
        }
    }

    override fun close() {}
}