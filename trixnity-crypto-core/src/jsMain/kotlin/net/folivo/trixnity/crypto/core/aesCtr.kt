package net.folivo.trixnity.crypto.core

import createCipheriv
import io.ktor.util.*
import js.objects.jso
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.toByteArray
import web.crypto.*

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow {
    return if (PlatformUtils.IS_BROWSER) {
        flow {// TODO should be streaming!
            val crypto = crypto.subtle
            val aesKey = crypto.importKey(
                format = KeyFormat.raw,
                keyData = key.toUint8Array(),
                algorithm = jso<AesKeyAlgorithm> { name = "AES-CTR" },
                extractable = false,
                keyUsages = arrayOf(KeyUsage.encrypt, KeyUsage.decrypt),
            )
            val result = Uint8Array(
                crypto.encrypt(
                    algorithm = jso<AesCtrParams> {
                        name = "AES-CTR"
                        counter = initialisationVector.toUint8Array()
                        length = 64
                    },
                    key = aesKey,
                    data = filterNotEmpty().toByteArray().toUint8Array(),
                )
            ).toByteArray()
            emit(result)
        }.filterNotEmpty()
    } else {
        flow {
            val cipher =
                createCipheriv(
                    algorithm = "aes-256-ctr",
                    key = key.toUint8Array(),
                    iv = initialisationVector.toUint8Array()
                )
            filterNotEmpty().collect { input ->
                emit(Uint8Array(cipher.update(input.toUint8Array())).toByteArray())
            }
            emit(Uint8Array(cipher.final()).toByteArray())
        }.filterNotEmpty()
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
                    format = KeyFormat.raw,
                    keyData = key.toUint8Array(),
                    algorithm = jso<AesKeyAlgorithm> { name = "AES-CTR" },
                    extractable = false,
                    keyUsages = arrayOf(KeyUsage.encrypt, KeyUsage.decrypt),
                )
                val result = Uint8Array(
                    crypto.decrypt(
                        algorithm = jso<AesCtrParams> {
                            name = "AES-CTR"
                            counter = initialisationVector.toUint8Array()
                            length = 64
                        },
                        key = aesKey,
                        data = filterNotEmpty().toByteArray().toUint8Array(),
                    )
                ).toByteArray()
                emit(result)
            } catch (exception: Throwable) {
                throw AesDecryptionException(exception)
            }
        }.filterNotEmpty()
    } else {
        flow {
            try {
                val decipher =
                    createCipheriv(
                        algorithm = "aes-256-ctr",
                        key = key.toUint8Array(),
                        iv = initialisationVector.toUint8Array()
                    )
                filterNotEmpty().collect { input ->
                    emit(Uint8Array(decipher.update(input.toUint8Array())).toByteArray())
                }
                emit(Uint8Array(decipher.final()).toByteArray())
            } catch (exception: Throwable) {
                throw AesDecryptionException(exception)
            }
        }.filterNotEmpty()
    }
}