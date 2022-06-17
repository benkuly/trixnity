package net.folivo.trixnity.client.crypto

import com.soywiz.korio.util.toByteArray
import com.soywiz.korio.util.toInt8Array
import createHash
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await

actual suspend fun sha256(input: ByteArray): String {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        crypto.digest("SHA-256", input.toInt8Array().buffer).await()
            .toByteArray()
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    } else {
        val hash = createHash("sha256")
        hash.update(input.toInt8Array())
        hash.digest("hex")
    }
}