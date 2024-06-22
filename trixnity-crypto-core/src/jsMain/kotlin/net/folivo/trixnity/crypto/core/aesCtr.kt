package net.folivo.trixnity.crypto.core

import createCipheriv
import io.ktor.util.*
import js.objects.jso
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import web.crypto.*

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow {
    return if (PlatformUtils.IS_BROWSER) {
        flow {
            val crypto = crypto.subtle
            val aesKey = crypto.importKey(
                format = KeyFormat.raw,
                keyData = key.toUint8Array(),
                algorithm = jso<AesKeyAlgorithm> { name = "AES-CTR" },
                extractable = false,
                keyUsages = arrayOf(KeyUsage.encrypt, KeyUsage.decrypt),
            )
            val nonce = initialisationVector.copyOf(8)
            var currentCounter = initialisationVector.copyOfRange(9, 16).toLong()
            var previousInput = ByteArray(0)
            filterNotEmpty().collect { nextInput ->
                val input = previousInput + nextInput
                val output = Uint8Array(
                    crypto.encrypt(
                        algorithm = jso<AesCtrParams> {
                            name = "AES-CTR"
                            counter = (nonce + currentCounter.toByteArray()).toUint8Array()
                            length = 64
                        },
                        key = aesKey,
                        data = input.toUint8Array(),
                    )
                ).toByteArray()
                val nextOutput = output.copyOfRange(previousInput.size, output.size)
                emit(nextOutput)
                val inputSize = input.size / 16
                currentCounter += inputSize
                previousInput = input.copyOfRange(inputSize * 16, input.size)
            }
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
        flow {
            try {
                val crypto = crypto.subtle
                val aesKey = crypto.importKey(
                    format = KeyFormat.raw,
                    keyData = key.toUint8Array(),
                    algorithm = jso<AesKeyAlgorithm> { name = "AES-CTR" },
                    extractable = false,
                    keyUsages = arrayOf(KeyUsage.encrypt, KeyUsage.decrypt),
                )
                val nonce = initialisationVector.copyOf(8)
                var currentCounter = initialisationVector.copyOfRange(9, 16).toLong()
                var previousInput = ByteArray(0)
                filterNotEmpty().collect { nextInput ->
                    val input = previousInput + nextInput
                    val output = Uint8Array(
                        crypto.decrypt(
                            algorithm = jso<AesCtrParams> {
                                name = "AES-CTR"
                                counter = (nonce + currentCounter.toByteArray()).toUint8Array()
                                length = 64
                            },
                            key = aesKey,
                            data = input.toUint8Array(),
                        )
                    ).toByteArray()
                    val nextOutput = output.copyOfRange(previousInput.size, output.size)
                    emit(nextOutput)
                    val inputSize = input.size / 16
                    currentCounter += inputSize
                    previousInput = input.copyOfRange(inputSize * 16, input.size)
                }
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

private fun ByteArray.toLong(): Long {
    require(size <= 8)
    var result = 0L
    forEach { result = (result shl 8) + it }
    return result
}

private fun Long.toByteArray(): ByteArray {
    var intermediate = this
    val result = ByteArray(8)
    repeat(result.size) { i ->
        result[i] = intermediate.toByte()
        intermediate = intermediate shr 8
    }
    return result
}