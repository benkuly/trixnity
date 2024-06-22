package net.folivo.trixnity.crypto.core

import io.ktor.util.*
import js.objects.jso
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import pbkdf2
import web.crypto.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
            keyData = password.encodeToByteArray().toUint8Array(),
            algorithm = jso<Algorithm> { name = "PBKDF2" },
            extractable = false,
            keyUsages = arrayOf(KeyUsage.deriveBits)
        )
        Uint8Array(
            crypto.deriveBits(
                algorithm = jso<Pbkdf2Params> {
                    name = "PBKDF2"
                    this.salt = salt.toUint8Array()
                    iterations = iterationCount
                    hash = "SHA-512"
                },
                baseKey = key,
                length = keyBitLength,
            )
        ).toByteArray()
    } else {
        suspendCoroutine { continuation ->
            pbkdf2(
                password = password,
                salt = salt.toUint8Array(),
                iterations = iterationCount,
                keylen = keyBitLength / 8,
                digest = "sha512"
            ) { err, key ->
                if (err != null) continuation.resumeWithException(err)
                continuation.resume(key.toByteArray())
            }
        }
    }
}