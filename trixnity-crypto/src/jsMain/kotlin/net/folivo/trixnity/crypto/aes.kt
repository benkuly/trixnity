package net.folivo.trixnity.crypto

import createCipheriv
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.js.json

actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val aesKey = crypto.importKey(
            "raw",
            key.toInt8Array().buffer,
            json("name" to "AES-CTR"),
            false,
            arrayOf("encrypt", "decrypt"),
        ).await()
        crypto.encrypt(
            json(
                "name" to "AES-CTR",
                "counter" to initialisationVector.toInt8Array().buffer,
                "length" to 64,
            ),
            aesKey,
            content.toInt8Array().buffer,
        ).await().toByteArray()
    } else {
        val cipher =
            createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
        cipher.update(content.toInt8Array()).toByteArray() + cipher.final().toByteArray()
    }
}

actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    try {
        return if (PlatformUtils.IS_BROWSER) {
            val crypto = crypto.subtle
            val aesKey = crypto.importKey(
                "raw",
                key.toInt8Array().buffer,
                json("name" to "AES-CTR"),
                false,
                arrayOf("encrypt", "decrypt"),
            ).await()
            crypto.decrypt(
                json(
                    "name" to "AES-CTR",
                    "counter" to initialisationVector.toInt8Array().buffer,
                    "length" to 64,
                ),
                aesKey,
                encryptedContent.toInt8Array().buffer,
            ).await().toByteArray()
        } else {
            val decipher =
                createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
            decipher.update(encryptedContent.toInt8Array()).toByteArray() + decipher.final().toByteArray()
        }
    } catch (exception: Throwable) {
        throw DecryptionException.OtherException(exception)
    }
}