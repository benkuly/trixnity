package net.folivo.trixnity.crypto

import createCipheriv
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.js.json

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow {
    return if (PlatformUtils.IS_BROWSER) {
        flow {// TODO should be streaming!
            val crypto = crypto.subtle
            val aesKey = crypto.importKey(
                "raw",
                key.toInt8Array().buffer,
                json("name" to "AES-CTR"),
                false,
                arrayOf("encrypt", "decrypt"),
            ).await()
            val result = crypto.encrypt(
                json(
                    "name" to "AES-CTR",
                    "counter" to initialisationVector.toInt8Array().buffer,
                    "length" to 64,
                ),
                aesKey,
                toByteArray().toInt8Array().buffer,
            ).await().toByteArray()
            emit(result)
        }
    } else {
        flow {
            val cipher =
                createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
            collect { input ->
                emit(cipher.update(input.toInt8Array()).toByteArray())
            }
            emit(cipher.final().toByteArray())
        }
    }
}

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow {
    return if (PlatformUtils.IS_BROWSER) {
        flow {// TODO should be streaming!
            try {
                val crypto = crypto.subtle
                val aesKey = crypto.importKey(
                    "raw",
                    key.toInt8Array().buffer,
                    json("name" to "AES-CTR"),
                    false,
                    arrayOf("encrypt", "decrypt"),
                ).await()
                val result = crypto.decrypt(
                    json(
                        "name" to "AES-CTR",
                        "counter" to initialisationVector.toInt8Array().buffer,
                        "length" to 64,
                    ),
                    aesKey,
                    toByteArray().toInt8Array().buffer,
                ).await().toByteArray()
                emit(result)
            } catch (exception: Throwable) {
                throw DecryptionException.OtherException(exception)
            }
        }
    } else {
        flow {
            try {
                val decipher =
                    createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
                collect { input ->
                    emit(decipher.update(input.toInt8Array()).toByteArray())
                }
                emit(decipher.final().toByteArray())
            } catch (exception: Throwable) {
                throw DecryptionException.OtherException(exception)
            }
        }
    }
}