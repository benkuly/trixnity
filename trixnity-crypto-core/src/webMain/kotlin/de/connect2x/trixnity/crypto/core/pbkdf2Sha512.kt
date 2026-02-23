package de.connect2x.trixnity.crypto.core

import io.ktor.util.*
import js.array.jsArrayOf
import js.buffer.toByteArray
import js.errors.toThrowable
import js.objects.unsafeJso
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import pbkdf2
import web.crypto.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.toJsNumber
import kotlin.js.toJsString

actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val key = crypto.importKey(
            format = KeyFormat.raw,
            keyData = password.encodeToByteArray().fastToUint8Array(),
            algorithm = unsafeJso<Algorithm> {
                name = "PBKDF2"
            },
            extractable = false,
            keyUsages = jsArrayOf(KeyUsage.deriveBits)
        )
        crypto.deriveBits(
            algorithm = unsafeJso<Pbkdf2Params> {
                this.name = "PBKDF2"
                this.salt = salt.fastToUint8Array()
                this.iterations = iterationCount
                this.hash = "SHA-512".toJsString()
            },
            baseKey = key,
            length = keyBitLength,
        ).toByteArray()
    } else {
        suspendCoroutine { continuation ->
            pbkdf2(
                password = password,
                salt = salt.toUint8Array(),
                iterations = iterationCount.toJsNumber(),
                keylen = (keyBitLength / 8).toJsNumber(),
                digest = "sha512"
            ) { err, key ->
                if (err != null) continuation.resumeWithException(err.toThrowable())
                continuation.resume(key.toByteArray())
            }
        }
    }
}