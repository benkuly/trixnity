package net.folivo.trixnity.crypto

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@OptIn(FlowPreview::class)
actual fun ByteFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow = flow {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val keySpec: Key = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(initialisationVector)

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

    emitAll(
        flatMapConcat { input ->
            cipher.update(byteArrayOf(input)).toByteFlow()
        }.onCompletion {
            cipher.doFinal()?.also { emitAll(it.toByteFlow()) }
        }
    )
}

@OptIn(FlowPreview::class)
actual fun ByteFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteFlow = flow {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        emitAll(
            flatMapConcat { input ->
                cipher.update(byteArrayOf(input)).toByteFlow()
            }.onCompletion {
                cipher.doFinal()?.also { emitAll(it.toByteFlow()) }
            }
        )
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}