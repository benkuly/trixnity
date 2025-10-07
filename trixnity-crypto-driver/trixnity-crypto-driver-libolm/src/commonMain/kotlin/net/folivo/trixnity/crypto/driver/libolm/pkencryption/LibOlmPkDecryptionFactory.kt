package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryptionFactory
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmPkDecryptionFactory : PkDecryptionFactory {
    override fun invoke(): LibOlmPkDecryption = LibOlmPkDecryption(OlmPkDecryption.create())
    override fun invoke(bytes: ByteArray): LibOlmPkDecryption = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPkDecryption = LibOlmPkDecryption(OlmPkDecryption.create(base64))
}