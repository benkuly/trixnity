package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.pkencryption.PkEncryptionFactory
import net.folivo.trixnity.olm.OlmPkEncryption
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmPkEncryptionFactory : PkEncryptionFactory {
    override fun invoke(bytes: ByteArray): LibOlmPkEncryption = this(bytes.encodeUnpaddedBase64())
    override fun invoke(base64: String): LibOlmPkEncryption = LibOlmPkEncryption(OlmPkEncryption.create(base64))
}