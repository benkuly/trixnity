package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519SignatureFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmEd25519SignatureFactory : Ed25519SignatureFactory {
    override fun invoke(bytes: ByteArray): LibOlmEd25519Signature = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmEd25519Signature = LibOlmEd25519Signature(base64)
}