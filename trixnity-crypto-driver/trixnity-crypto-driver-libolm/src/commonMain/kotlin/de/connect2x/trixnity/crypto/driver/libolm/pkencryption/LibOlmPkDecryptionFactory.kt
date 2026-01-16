package de.connect2x.trixnity.crypto.driver.libolm.pkencryption

import de.connect2x.trixnity.crypto.driver.pkencryption.PkDecryptionFactory
import de.connect2x.trixnity.libolm.OlmPkDecryption
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmPkDecryptionFactory : PkDecryptionFactory {
    override fun invoke(): LibOlmPkDecryption = LibOlmPkDecryption(OlmPkDecryption.create())
    override fun invoke(bytes: ByteArray): LibOlmPkDecryption = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPkDecryption = LibOlmPkDecryption(OlmPkDecryption.create(base64))
}