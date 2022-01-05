package net.folivo.trixnity.client.key

import io.ktor.util.*
import net.folivo.trixnity.client.crypto.decryptAes256Ctr
import net.folivo.trixnity.client.crypto.encryptAes256Ctr
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData
import kotlin.experimental.and
import kotlin.random.Random

expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

internal class DerivedKeys(val aesKey: ByteArray, val hmacKey: ByteArray)

internal fun deriveKeys(key: ByteArray, name: String): DerivedKeys {
    val zerosalt = ByteArray(32)
    val hashedKey = hmacSha256(zerosalt, key)
    val aesKey = hmacSha256(hashedKey, name.encodeToByteArray() + ByteArray(1) { 0x01 })
    val hmacKey = hmacSha256(hashedKey, aesKey + name.encodeToByteArray() + ByteArray(1) { 0x02 })
    return DerivedKeys(aesKey = aesKey, hmacKey = hmacKey)
}

@OptIn(InternalAPI::class)
internal suspend fun encryptAesHmacSha2(
    content: ByteArray,
    key: ByteArray,
    name: String,
    initialisationVector: ByteArray = Random.nextBytes(16)
): AesHmacSha2EncryptedData {
    val iv = initialisationVector.copyOf()
    iv[8] = iv[8] and 0x7f
    val keys = deriveKeys(key, name)
    val ciphertext = encryptAes256Ctr(
        content = content,
        key = keys.aesKey,
        initialisationVector = iv
    )
    return AesHmacSha2EncryptedData(
        iv = iv.encodeBase64(),
        ciphertext = ciphertext.encodeBase64(),
        mac = hmacSha256(keys.hmacKey, ciphertext).encodeBase64()
    )
}

@OptIn(InternalAPI::class)
internal suspend fun decryptAesHmacSha2(
    content: AesHmacSha2EncryptedData,
    key: ByteArray,
    name: String,
): ByteArray {
    val keys = deriveKeys(key, name)
    val ciphertextBytes = content.ciphertext.decodeBase64Bytes()
    val hmac = hmacSha256(keys.hmacKey, ciphertextBytes).encodeBase64()
    if (hmac != content.mac) throw IllegalArgumentException("bad mac")
    return decryptAes256Ctr(
        encryptedContent = ciphertextBytes,
        key = keys.aesKey,
        initialisationVector = content.iv.decodeBase64Bytes()
    )
}