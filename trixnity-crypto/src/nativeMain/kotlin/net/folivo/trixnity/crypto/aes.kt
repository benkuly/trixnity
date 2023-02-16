package net.folivo.trixnity.crypto

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteArrayFlow
import net.folivo.trixnity.crypto.olm.DecryptionException

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {// TODO should be streaming!
    emitAll(AES.encryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding).toByteArrayFlow())
}

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {// TODO should be streaming!
    try {
        emitAll(AES.decryptAesCtr(toByteArray(), key, initialisationVector, Padding.NoPadding).toByteArrayFlow())
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}