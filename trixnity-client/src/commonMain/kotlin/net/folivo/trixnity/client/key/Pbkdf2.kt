package net.folivo.trixnity.client.key

// TODO can be implemented multiplatform krypto
internal expect suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray