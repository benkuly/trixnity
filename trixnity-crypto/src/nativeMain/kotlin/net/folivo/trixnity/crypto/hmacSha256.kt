package net.folivo.trixnity.crypto

import com.soywiz.krypto.HMAC

actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    HMAC.hmacSHA256(key, data).bytes