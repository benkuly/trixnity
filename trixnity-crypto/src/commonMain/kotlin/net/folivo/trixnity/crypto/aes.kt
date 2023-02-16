package net.folivo.trixnity.crypto

import net.folivo.trixnity.core.ByteArrayFlow


class AesDecryptionException(reason: Exception) : Exception(reason)

expect fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow

expect fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow