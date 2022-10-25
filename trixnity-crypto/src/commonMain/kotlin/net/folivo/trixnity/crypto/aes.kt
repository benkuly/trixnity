package net.folivo.trixnity.crypto

import net.folivo.trixnity.core.ByteFlow


class AesDecryptionException(reason: Exception) : Exception(reason)

expect fun ByteFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow

expect fun ByteFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow