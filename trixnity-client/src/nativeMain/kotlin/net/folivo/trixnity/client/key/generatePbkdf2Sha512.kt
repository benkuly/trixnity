package net.folivo.trixnity.client.key

import com.soywiz.krypto.PBKDF2
import io.ktor.utils.io.core.*

internal actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray =
    PBKDF2.pbkdf2WithHmacSHA512(password.toByteArray(), salt, iterationCount, keyBitLength)