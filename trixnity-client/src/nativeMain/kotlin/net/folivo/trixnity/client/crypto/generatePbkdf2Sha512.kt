package net.folivo.trixnity.client.crypto

import com.soywiz.krypto.PBKDF2

internal actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray =
    PBKDF2.pbkdf2WithHmacSHA512(password.encodeToByteArray(), salt, iterationCount, keyBitLength).bytes