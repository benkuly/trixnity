package net.folivo.trixnity.client.crypto

class Aes256CtrInfo(
    val encryptedContent: ByteArray,
    val initialisationVector: ByteArray,
    val key: ByteArray
)

expect suspend fun encryptAes256Ctr(content: ByteArray): Aes256CtrInfo
expect suspend fun decryptAes256Ctr(content: Aes256CtrInfo): ByteArray