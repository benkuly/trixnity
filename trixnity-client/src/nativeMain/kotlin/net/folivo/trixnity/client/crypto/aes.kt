package net.folivo.trixnity.client.crypto

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding

internal actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray = AES.encryptAesCtr(content, key, initialisationVector, Padding.NoPadding)

internal actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray = try {
    AES.decryptAesCtr(encryptedContent, key, initialisationVector, Padding.NoPadding)
} catch (exception: Exception) {
    throw DecryptionException.OtherException(exception)
}