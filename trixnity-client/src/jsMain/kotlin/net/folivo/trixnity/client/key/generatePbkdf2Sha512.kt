package net.folivo.trixnity.client.key

import com.soywiz.korio.util.toByteArray
import com.soywiz.korio.util.toInt8Array
import crypto
import kotlinx.coroutines.await
import kotlin.js.json

internal actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    val crypto = crypto?.subtle
    return if (crypto != null) {
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
                "salt" to salt.toInt8Array().buffer,
                "iterations" to iterationCount,
                "hash" to "SHA-512"
            ),
            key,
            keyBitLength,
        ).await()
        keybits.toByteArray()
    } else {
        throw RuntimeException("missing browser crypto (nodejs not supported yet)")
    }
}