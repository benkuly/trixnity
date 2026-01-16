package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkEncryptionFactory
import de.connect2x.trixnity.libolm.OlmPkEncryption
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmPkEncryptionFactory : PkEncryptionFactory {
    override fun invoke(bytes: ByteArray): LibOlmPkEncryption = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPkEncryption = LibOlmPkEncryption(OlmPkEncryption.create(base64))
}