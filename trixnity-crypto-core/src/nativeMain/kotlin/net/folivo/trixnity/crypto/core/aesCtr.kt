package net.folivo.trixnity.crypto.core

import korlibs.crypto.AES
import korlibs.crypto.Padding
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.toByteArray

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {// TODO should be streaming!
    emit(AES.encryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding))
}

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {// TODO should be streaming!
    try {
        emit(AES.decryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding))
    } catch (exception: Exception) {
        throw AesDecryptionException(exception)
    }
}