package net.folivo.trixnity.crypto

import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import pbkdf2
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.json

actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    val saltBuffer = salt.toInt8Array().buffer
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val key = crypto.importKey(
            format = "raw",
            keyData = password.encodeToByteArray().toInt8Array().buffer,
            algorithm = "PBKDF2",
            extractable = false,
            keyUsages = arrayOf("deriveBits")
        ).await()
        val keybits = crypto.deriveBits(
            json(
                "name" to "PBKDF2",
                "salt" to saltBuffer,
                "iterations" to iterationCount,
                "hash" to "SHA-512"
            ),
            key,
            keyBitLength,
        ).await()
        keybits.toByteArray()
    } else {
        suspendCoroutine { continuation ->
            pbkdf2(
                password,
                salt.toInt8Array(),
                iterationCount,
                keyBitLength / 8,
                "sha512"
            ) { err, key ->
                if (err != null) continuation.resumeWithException(err)
                continuation.resume(key.toByteArray())
            }
        }
    }
}