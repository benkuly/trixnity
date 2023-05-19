package net.folivo.trixnity.crypto.core

import io.ktor.util.*
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.experimental.and

class DerivedKeys(val aesKey: ByteArray, val hmacKey: ByteArray)

suspend fun deriveKeys(key: ByteArray, name: String): DerivedKeys {
    val zerosalt = ByteArray(32)
    val hashedKey = hmacSha256(zerosalt, key)
    val aesKey = hmacSha256(hashedKey, name.encodeToByteArray() + ByteArray(1) { 0x01 })
    val hmacKey = hmacSha256(hashedKey, aesKey + name.encodeToByteArray() + ByteArray(1) { 0x02 })
    return DerivedKeys(aesKey = aesKey, hmacKey = hmacKey)
}

data class AesHmacSha2EncryptedData(
    val iv: String,
    val ciphertext: String,
    val mac: String
)

suspend fun encryptAesHmacSha2(
    content: ByteArray,
    key: ByteArray,
    name: String,
    initialisationVector: ByteArray = SecureRandom.nextBytes(16)
): AesHmacSha2EncryptedData {
    val iv = initialisationVector.copyOf()
    iv[8] = iv[8] and 0x7f
    val keys = deriveKeys(key, name)
    val ciphertext = content.toByteArrayFlow().encryptAes256Ctr(
        key = keys.aesKey,
        initialisationVector = iv
    ).toByteArray()
    return AesHmacSha2EncryptedData(
        iv = iv.encodeBase64(),
        ciphertext = ciphertext.encodeBase64(),
        mac = hmacSha256(keys.hmacKey, ciphertext).encodeBase64()
    )
}

suspend fun decryptAesHmacSha2(
    content: AesHmacSha2EncryptedData,
    key: ByteArray,
    name: String,
): ByteArray {
    val keys = deriveKeys(key, name)
    val ciphertextBytes = content.ciphertext.decodeBase64Bytes()
    val hmac = hmacSha256(keys.hmacKey, ciphertextBytes).encodeBase64()
    if (hmac != content.mac) throw IllegalArgumentException("bad mac")
    return ciphertextBytes.toByteArrayFlow().decryptAes256Ctr(
        key = keys.aesKey,
        initialisationVector = content.iv.decodeBase64Bytes()
    ).toByteArray()
}

suspend fun createAesHmacSha2MacFromKey(key: ByteArray, iv: ByteArray): String {
    return encryptAesHmacSha2(
        content = ByteArray(32),
        key = key,
        name = "",
        initialisationVector = iv
    ).mac
}