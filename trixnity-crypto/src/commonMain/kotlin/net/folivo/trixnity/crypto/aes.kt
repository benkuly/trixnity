package net.folivo.trixnity.crypto


class AesDecryptionException(reason: Exception) : Exception(reason)

expect suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray

expect suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray