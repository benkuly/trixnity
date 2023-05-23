package net.folivo.trixnity.crypto.core

import korlibs.crypto.PBKDF2


actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray =
    PBKDF2.pbkdf2WithHmacSHA512(password.encodeToByteArray(), salt, iterationCount, keyBitLength).bytes