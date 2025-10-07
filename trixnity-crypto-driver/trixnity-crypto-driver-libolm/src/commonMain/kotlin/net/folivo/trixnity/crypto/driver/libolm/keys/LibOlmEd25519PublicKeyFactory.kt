package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKeyFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmEd25519PublicKeyFactory : Ed25519PublicKeyFactory {
    override fun invoke(bytes: ByteArray): LibOlmEd25519PublicKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmEd25519PublicKey = LibOlmEd25519PublicKey(base64)
}