package net.folivo.trixnity.client.key

internal expect suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray