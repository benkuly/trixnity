package net.folivo.trixnity.client.crypto

internal expect suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray