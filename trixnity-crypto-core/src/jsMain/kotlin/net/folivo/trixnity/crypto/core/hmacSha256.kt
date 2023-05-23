package net.folivo.trixnity.crypto.core

import createHmac
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import kotlin.js.json

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val hmacKey = crypto.importKey(
            format = "raw",
            keyData = key.toInt8Array().buffer,
            algorithm = json("name" to "HMAC", "hash" to json("name" to "SHA-256")),
            extractable = false,
            keyUsages = arrayOf("sign")
        ).await()
        crypto.sign(
            algorithm = "HMAC",
            key = hmacKey,
            data = data.toInt8Array().buffer
        ).await().toByteArray()
    } else {
        val hmac = createHmac("sha256", key.toInt8Array())
        hmac.update(data.toInt8Array())
        hmac.digest().toByteArray()
    }
}