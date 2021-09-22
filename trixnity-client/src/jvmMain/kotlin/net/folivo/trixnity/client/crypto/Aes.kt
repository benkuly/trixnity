package net.folivo.trixnity.client.crypto

import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual suspend fun encryptAes256Ctr(content: ByteArray): Aes256CtrInfo {
    val secureRandom = SecureRandom()
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val key = ByteArray(256 / 8)
    secureRandom.nextBytes(key)
    val nonce = ByteArray(64 / 8)
    secureRandom.nextBytes(nonce)
    val initialisationVector = nonce + ByteArray(64 / 8)
    val keySpec: Key = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(initialisationVector)

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    return Aes256CtrInfo(
        encryptedContent = cipher.doFinal(content),
        initialisationVector = initialisationVector,
        key = key
    )
}

actual suspend fun decryptAes256Ctr(content: Aes256CtrInfo): ByteArray {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(content.key, "AES")
        val ivSpec = IvParameterSpec(content.initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(content.encryptedContent)
    } catch (exception: Throwable) {
        throw DecryptionException.OtherException(exception)
    }
}