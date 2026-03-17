package de.connect2x.trixnity.crypto.core

import createHmac
import io.ktor.util.*
import js.array.jsArrayOf
import js.buffer.toByteArray
import js.objects.unsafeJso
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import web.crypto.*

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val hmacKey = crypto.importKey(
            format = KeyFormat.raw,
            keyData = key.fastToUint8Array(),
            algorithm = unsafeJso<HmacImportParams> {
                name = "HMAC"
                hash = AlgorithmIdentifier("SHA-256")
            },
            extractable = false,
            keyUsages = jsArrayOf(KeyUsage.sign)
        )
        crypto.sign(
            algorithm = "HMAC",
            key = hmacKey,
            data = data.fastToUint8Array()
        ).toByteArray()
    } else {
        val hmac = createHmac(algorithm = "sha256", key = key.toUint8Array())
        hmac.update(data.toUint8Array())
        Uint8Array(hmac.digest()).toByteArray()
    }
}