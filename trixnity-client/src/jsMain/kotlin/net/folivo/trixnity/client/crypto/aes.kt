package net.folivo.trixnity.client.crypto

internal actual suspend fun encryptAes256Ctr(
    content: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    TODO("Not yet implemented")
}

internal actual suspend fun decryptAes256Ctr(
    encryptedContent: ByteArray,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArray {
    TODO("Not yet implemented")
}