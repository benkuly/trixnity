package net.folivo.trixnity.crypto.core

import createHmac
import io.ktor.util.*
import js.core.jso
import js.promise.await
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import web.crypto.HmacImportParams
import web.crypto.KeyFormat
import web.crypto.KeyUsage
import web.crypto.crypto
import kotlin.js.json

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val hmacKey = crypto.importKey(
            format = KeyFormat.raw,
            keyData = key.toUint8Array(),
            algorithm = jso<HmacImportParams> {
                name = "HMAC"
                hash = json("name" to "SHA-256")
            },
            extractable = false,
            keyUsages = arrayOf(KeyUsage.sign)
        ).await()
        Uint8Array(
            crypto.sign(
                algorithm = "HMAC",
                key = hmacKey,
                data = data.toUint8Array()
            ).await()
        ).toByteArray()
    } else {
        val hmac = createHmac(algorithm = "sha256", key = key.toUint8Array())
        hmac.update(data.toUint8Array())
        Uint8Array(hmac.digest()).toByteArray()
    }
}