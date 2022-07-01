package net.folivo.trixnity.crypto

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding
import net.folivo.trixnity.crypto.olm.DecryptionException

actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray = AES.encryptAesCtr(content, key, initialisationVector, Padding.NoPadding)

actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray = try {
    AES.decryptAesCtr(encryptedContent, key, initialisationVector, Padding.NoPadding)
} catch (exception: Exception) {
    throw DecryptionException.OtherException(exception)
}