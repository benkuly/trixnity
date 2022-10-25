package net.folivo.trixnity.crypto

import createCipheriv
import crypto
import io.ktor.util.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.js.json

@OptIn(FlowPreview::class)
actual fun ByteFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow {
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
            crypto.encrypt(
                json(
                    "name" to "AES-CTR",
                    "counter" to initialisationVector.toInt8Array().buffer,
                    "length" to 64,
                ),
                aesKey,
                toByteArray().toInt8Array().buffer,
            ).await().toByteArray().toByteFlow()
                .also { emitAll(it) }
        }
    } else {
        flow {
            val cipher =
                createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
            emitAll(
                flatMapConcat { input ->
                    cipher.update(byteArrayOf(input).toInt8Array()).toByteArray().toByteFlow()
                }.onCompletion {
                    cipher.final().also { emitAll(it.toByteArray().toByteFlow()) }
                }
            )
        }
    }
}

@OptIn(FlowPreview::class)
actual fun ByteFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow {
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
                crypto.decrypt(
                    json(
                        "name" to "AES-CTR",
                        "counter" to initialisationVector.toInt8Array().buffer,
                        "length" to 64,
                    ),
                    aesKey,
                    toByteArray().toInt8Array().buffer,
                ).await().toByteArray().toByteFlow()
                    .also { emitAll(it) }
            } catch (exception: Throwable) {
                throw DecryptionException.OtherException(exception)
            }
        }
    } else {
        flow {
            try {
                val decipher =
                    createCipheriv("aes-256-ctr", key.toInt8Array(), initialisationVector.toInt8Array())
                emitAll(
                    flatMapConcat { input ->
                        decipher.update(byteArrayOf(input).toInt8Array()).toByteArray().toByteFlow()
                    }.onCompletion {
                        decipher.final().also { emitAll(it.toByteArray().toByteFlow()) }
                    }
                )
            } catch (exception: Throwable) {
                throw DecryptionException.OtherException(exception)
            }
        }
    }
}