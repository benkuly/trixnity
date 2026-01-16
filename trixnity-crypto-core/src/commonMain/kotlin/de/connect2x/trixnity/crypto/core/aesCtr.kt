package de.connect2x.trixnity.crypto.core

import de.connect2x.trixnity.utils.ByteArrayFlow


class AesDecryptionException(reason: Throwable) : Exception(reason)

expect fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow

expect fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow