package net.folivo.trixnity.crypto

import net.folivo.trixnity.crypto.olm.DecryptionException
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual suspend fun encryptAes256Ctr(
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

actual suspend fun decryptAes256Ctr(
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
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}