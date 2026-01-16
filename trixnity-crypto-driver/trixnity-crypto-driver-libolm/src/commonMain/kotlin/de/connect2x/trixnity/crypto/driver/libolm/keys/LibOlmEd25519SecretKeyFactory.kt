package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.Ed25519SecretKeyFactory
import de.connect2x.trixnity.libolm.OlmPkSigning
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmEd25519SecretKeyFactory : Ed25519SecretKeyFactory {
    override fun invoke(): LibOlmEd25519SecretKey = LibOlmEd25519SecretKey(OlmPkSigning.create())
    override fun invoke(bytes: ByteArray): LibOlmEd25519SecretKey = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmEd25519SecretKey = LibOlmEd25519SecretKey(OlmPkSigning.create(base64))
}