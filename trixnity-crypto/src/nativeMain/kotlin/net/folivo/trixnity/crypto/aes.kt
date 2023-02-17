package net.folivo.trixnity.crypto

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.crypto.olm.DecryptionException

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
        throw DecryptionException.OtherException(exception)
    }
}