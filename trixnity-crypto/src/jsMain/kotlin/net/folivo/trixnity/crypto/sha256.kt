package net.folivo.trixnity.crypto

import createHash
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import net.folivo.trixnity.olm.encodeUnpaddedBase64

actual suspend fun sha256(input: ByteArray): String {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        crypto.digest("SHA-256", input.toInt8Array().buffer).await()
            .toByteArray()
            .encodeUnpaddedBase64()
    } else {
        val hash = createHash("sha256")
        hash.update(input.toInt8Array())
        hash.digest().toByteArray()
            .encodeUnpaddedBase64()
    }
}