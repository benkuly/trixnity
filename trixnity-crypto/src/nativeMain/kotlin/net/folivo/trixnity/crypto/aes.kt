package net.folivo.trixnity.crypto

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.crypto.olm.DecryptionException

actual fun ByteFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow = flow {// TODO should be streaming!
    emitAll(AES.encryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding).toByteFlow())
}

actual fun ByteFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow = flow {// TODO should be streaming!
    try {
        emitAll(AES.decryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding).toByteFlow())
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}