package net.folivo.trixnity.crypto.core

import net.folivo.trixnity.utils.ByteArrayFlow


class AesDecryptionException(reason: Throwable) : Exception(reason)

expect fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow

expect fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow