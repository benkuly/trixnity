package net.folivo.trixnity.client.crypto

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val keySpec: Key = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(initialisationVector)

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    return cipher.doFinal(content)
}

internal actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encryptedContent)
    } catch (exception: Throwable) {
        throw DecryptionException.OtherException(exception)
    }
}