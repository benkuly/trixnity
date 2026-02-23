package de.connect2x.trixnity.crypto.core

import createCipheriv
import io.ktor.util.*
import js.buffer.ArrayBuffer
import js.buffer.BufferSource
import js.typedarrays.Uint8Array
import js.typedarrays.asInt8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import de.connect2x.trixnity.utils.ByteArrayFlow
import web.crypto.*

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow {
    return if (PlatformUtils.IS_BROWSER) {
        flow {
            val crypto = crypto.subtle
            aesOperation(
                this,
                key,
                initialisationVector
            ) { algorithm: AesCtrParams, cryptoKey: CryptoKey, data: BufferSource ->
                crypto.encrypt(algorithm, cryptoKey, data)
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
            val crypto = crypto.subtle
            try {
                aesOperation(
                    this,
                    key,
                    initialisationVector
                ) { algorithm: AesCtrParams, cryptoKey: CryptoKey, data: BufferSource ->
                    crypto.decrypt(algorithm, cryptoKey, data)
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

private suspend fun ByteArrayFlow.aesOperation(
    flowCollector: FlowCollector<ByteArray>,
    key: ByteArray,
    initialisationVector: ByteArray,
    operation: suspend (algorithm: AesCtrParams, key: CryptoKey, data: BufferSource) -> ArrayBuffer,
) {
    val crypto = crypto.subtle

    val aesKey = crypto.importKey(
        format = KeyFormat.raw,
        keyData = key.asInt8Array(),
        algorithm = AesKeyAlgorithm(name = "AES-CTR", length = 256),
        extractable = false,
        keyUsages = arrayOf(KeyUsage.encrypt, KeyUsage.decrypt),
    )
    // iv is composed of a nonce and counter in AES-CTR
    val nonce = initialisationVector.copyOf(8)
    val currentCounter = initialisationVector.copyOfRange(8, 16)
    var previousInput = ByteArray(0)
    filterNotEmpty().collect { nextInput ->
        val input = previousInput + nextInput
        val output = Uint8Array(
            operation(
                AesCtrParams(
                    name = "AES-CTR",
                    counter = (nonce + currentCounter).asInt8Array(),
                    length = 64,
                ),
                aesKey,
                input.asInt8Array(),
            )
        ).toByteArray()
        val nextOutput = output.copyOfRange(previousInput.size, output.size)
        flowCollector.emit(nextOutput)
        // the counter needs to be increased after a complete block is decrypted
        // a block is 128 bits (= 16 bytes) long
        val inputSize = input.size / 16
        // increase counter
        repeat(inputSize) {
            currentCounter.increment()
        }
        // as long as the block is not completely "used", we need to cache the previous part of the block
        previousInput = input.copyOfRange(inputSize * 16, input.size)
    }
}

private fun ByteArray.increment() {
    for (i in (size - 1) downTo 0) {
        if ((++this[i]).toInt() != 0) {
            break
        }
    }
}