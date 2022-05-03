package net.folivo.trixnity.client.crypto

import com.soywiz.korio.util.toByteArray
import com.soywiz.korio.util.toInt8Array
import crypto
import kotlinx.coroutines.await
import kotlin.js.json

internal actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    val crypto = crypto?.subtle
    return if (crypto != null) {
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
        throw RuntimeException("missing browser crypto (nodejs not supported yet)")
    }
}

internal actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    try {
        val crypto = crypto?.subtle
        return if (crypto != null) {
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
            throw RuntimeException("missing browser crypto (nodejs not supported yet)")
        }
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}